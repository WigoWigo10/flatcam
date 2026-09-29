#include <windows.h>
#include <jni.h>

#include <filesystem>
#include <cstring>
#include <iostream>
#include <string>
#include <vector>

// These exports belong to the process that creates the graphics context.
// A wrapper that starts java.exe would not give JavaFX these driver hints.
extern "C" {
__declspec(dllexport) DWORD NvOptimusEnablement = 0x00000001;
__declspec(dllexport) int AmdPowerXpressRequestHighPerformance = 1;
}

namespace {
namespace fs = std::filesystem;

using CreateJavaVm = jint(JNICALL*)(JavaVM**, void**, void*);

std::wstring environment(const wchar_t* name) {
    DWORD length = GetEnvironmentVariableW(name, nullptr, 0);
    if (length == 0) return {};
    std::wstring value(length, L'\0');
    DWORD copied = GetEnvironmentVariableW(name, value.data(), length);
    if (copied == 0 || copied >= length) return {};
    value.resize(copied);
    return value;
}

std::string utf8(const std::wstring& text) {
    if (text.empty()) return {};
    int length = WideCharToMultiByte(CP_UTF8, 0, text.data(), static_cast<int>(text.size()),
                                     nullptr, 0, nullptr, nullptr);
    std::string result(length, '\0');
    WideCharToMultiByte(CP_UTF8, 0, text.data(), static_cast<int>(text.size()),
                        result.data(), length, nullptr, nullptr);
    return result;
}

fs::path executablePath() {
    std::wstring path(MAX_PATH, L'\0');
    for (;;) {
        DWORD length = GetModuleFileNameW(nullptr, path.data(), static_cast<DWORD>(path.size()));
        if (length == 0) return {};
        if (length < path.size()) {
            path.resize(length);
            return fs::path(path);
        }
        path.resize(path.size() * 2);
    }
}

fs::path findJavaHome() {
    for (const wchar_t* variable : {L"FLATCAM_FX_JAVA_HOME", L"JAVA_HOME"}) {
        std::wstring value = environment(variable);
        if (!value.empty()) {
            fs::path home(value);
            if (fs::exists(home / "bin" / "server" / "jvm.dll")) return home;
        }
    }
    DWORD length = SearchPathW(nullptr, L"java.exe", nullptr, 0, nullptr, nullptr);
    if (length == 0) return {};
    std::wstring path(length, L'\0');
    DWORD copied = SearchPathW(nullptr, L"java.exe", nullptr, length, path.data(), nullptr);
    if (copied == 0 || copied >= length) return {};
    path.resize(copied);
    fs::path home = fs::path(path).parent_path().parent_path();
    return fs::exists(home / "bin" / "server" / "jvm.dll") ? home : fs::path{};
}

struct RuntimePaths {
    std::wstring classPath;
    std::wstring modulePath;
};

RuntimePaths runtimePaths(const fs::path& root) {
    RuntimePaths paths;
    for (const fs::path& classes : {
            root / "flatcam-fx" / "target" / "classes",
            root / "flatcam-application" / "target" / "classes",
            root / "flatcam-cam" / "target" / "classes"}) {
        if (!fs::is_directory(classes)) return {};
        if (!paths.classPath.empty()) paths.classPath += L';';
        paths.classPath += classes.wstring();
    }
    const fs::path dependencies = root / "flatcam-fx" / "target" / "dependency";
    if (!fs::is_directory(dependencies)) return {};
    int modules = 0;
    for (const auto& entry : fs::directory_iterator(dependencies)) {
        if (entry.is_regular_file() && entry.path().extension() == L".jar") {
            std::wstring name = entry.path().filename().wstring();
            bool javaFxJar = name.rfind(L"javafx-", 0) == 0;
            bool windowsJar = name.size() >= 8
                    && name.compare(name.size() - 8, 8, L"-win.jar") == 0;
            if (javaFxJar && windowsJar) {
                if (!paths.modulePath.empty()) paths.modulePath += L';';
                paths.modulePath += entry.path().wstring();
                ++modules;
            } else if (!javaFxJar) {
                paths.classPath += L';';
                paths.classPath += entry.path().wstring();
            }
        }
    }
    return modules >= 3 ? paths : RuntimePaths{};
}

bool describeJavaError(JNIEnv* env, const char* phase) {
    if (!env->ExceptionCheck()) return false;
    std::cerr << "FlatCAM FX native launcher: Java error during " << phase << "\n";
    env->ExceptionDescribe();
    env->ExceptionClear();
    return true;
}

}  // namespace

int wmain(int argc, wchar_t** argv) {
    bool probe = false;
    bool software = false;
    bool verbose = false;
    std::vector<std::wstring> applicationArgs;
    for (int i = 1; i < argc; ++i) {
        std::wstring arg = argv[i];
        if (arg == L"--probe") probe = true;
        else if (arg == L"--software") software = true;
        else if (arg == L"--verbose-gpu") verbose = true;
        else applicationArgs.push_back(arg);
    }

    // target/native/FlatCAMFX.exe -> reactor root
    const fs::path root = executablePath().parent_path().parent_path().parent_path();
    const fs::path javaHome = findJavaHome();
    const RuntimePaths paths = runtimePaths(root);
    if (javaHome.empty() || paths.classPath.empty() || paths.modulePath.empty()) {
        std::cerr << "FlatCAM FX native launcher: JDK or built dependencies not found. "
                     "Run build-native.cmd first.\n";
        return 2;
    }
    SetCurrentDirectoryW(root.c_str());
    // jvm.dll needs the sibling JDK DLLs from bin; this changes only this process.
    const fs::path javaBin = javaHome / "bin";
    SetDllDirectoryW(javaBin.c_str());
    const fs::path jvmPath = javaBin / "server" / "jvm.dll";
    HMODULE jvmLibrary = LoadLibraryW(jvmPath.c_str());
    if (jvmLibrary == nullptr) {
        std::cerr << "FlatCAM FX native launcher: cannot load jvm.dll (Windows error "
                  << GetLastError() << ").\n";
        return 2;
    }
    FARPROC symbol = GetProcAddress(jvmLibrary, "JNI_CreateJavaVM");
    static_assert(sizeof(CreateJavaVm) == sizeof(FARPROC));
    CreateJavaVm createVm = nullptr;
    std::memcpy(&createVm, &symbol, sizeof(createVm));
    if (createVm == nullptr) {
        std::cerr << "FlatCAM FX native launcher: JNI_CreateJavaVM is unavailable.\n";
        return 2;
    }

    std::vector<std::string> optionValues = {
        "-Djava.class.path=" + utf8(paths.classPath),
        "--module-path=" + utf8(paths.modulePath),
        "--add-modules=javafx.controls",
        "-Dfile.encoding=UTF-8",
        std::string("-Dprism.order=") + (software ? "sw" : "d3d,sw")
    };
    if (verbose) optionValues.emplace_back("-Dprism.verbose=true");
    std::vector<JavaVMOption> options;
    for (std::string& value : optionValues) options.push_back({value.data(), nullptr});
    JavaVMInitArgs vmArgs{JNI_VERSION_1_8, static_cast<jint>(options.size()), options.data(), JNI_FALSE};
    JavaVM* vm = nullptr;
    JNIEnv* env = nullptr;
    jint status = createVm(&vm, reinterpret_cast<void**>(&env), &vmArgs);
    if (status != JNI_OK || env == nullptr) {
        std::cerr << "FlatCAM FX native launcher: JVM initialization failed (" << status << ").\n";
        return 2;
    }

    std::cout << "FlatCAM FX native launcher: " << (software ? "software requested" : "D3D then software")
              << "; high-performance GPU requested from hybrid drivers." << std::endl;
    jclass applicationClass = env->FindClass("javafx/application/Application");
    bool failed = describeJavaError(env, "loading JavaFX") || applicationClass == nullptr;
    if (!failed && !probe) {
        jclass mainClass = env->FindClass("org/flatcam/fx/MainApp");
        failed = describeJavaError(env, "loading MainApp") || mainClass == nullptr;
        if (!failed) {
            jmethodID launch = env->GetStaticMethodID(applicationClass, "launch",
                    "(Ljava/lang/Class;[Ljava/lang/String;)V");
            failed = describeJavaError(env, "finding Application.launch") || launch == nullptr;
            if (!failed) {
                jclass stringClass = env->FindClass("java/lang/String");
                jobjectArray args = env->NewObjectArray(static_cast<jsize>(applicationArgs.size()),
                                                          stringClass, nullptr);
                for (jsize i = 0; i < static_cast<jsize>(applicationArgs.size()); ++i) {
                    jstring arg = env->NewString(
                            reinterpret_cast<const jchar*>(applicationArgs[i].data()),
                            static_cast<jsize>(applicationArgs[i].size()));
                    env->SetObjectArrayElement(args, i, arg);
                    env->DeleteLocalRef(arg);
                }
                env->CallStaticVoidMethod(applicationClass, launch, mainClass, args);
                failed = describeJavaError(env, "running JavaFX");
            }
        }
    }
    vm->DestroyJavaVM();
    return failed ? 1 : 0;
}
