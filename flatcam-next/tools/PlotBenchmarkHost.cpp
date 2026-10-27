// Test-only Windows JNI host. Requests the same hybrid-GPU preference as the production launcher.
// No registry edits, GPU forcing guarantee, application preferences or machine operations.
#include <windows.h>
#include <jni.h>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <string>
#include <vector>

extern "C" {
__declspec(dllexport) DWORD NvOptimusEnablement = 1;
__declspec(dllexport) int AmdPowerXpressRequestHighPerformance = 1;
}

int wmain(int argc, wchar_t** argv) {
    if (argc != 4) { std::cerr << "Expected JDK home, classpath file and JVM options file\n"; return 2; }
    namespace fs = std::filesystem;
    const fs::path javaBin = fs::path(argv[1]) / L"bin";
    SetDllDirectoryW(javaBin.c_str());
    HMODULE library = LoadLibraryW((javaBin / L"server" / L"jvm.dll").c_str());
    if (!library) { std::cerr << "Could not load jvm.dll: " << GetLastError() << '\n'; return 2; }
    auto createVm = reinterpret_cast<jint (JNICALL *)(JavaVM**, void**, void*)>(GetProcAddress(library, "JNI_CreateJavaVM"));
    if (!createVm) return 2;
    std::ifstream classFile{fs::path(argv[2])};
    std::string classPath;
    std::getline(classFile, classPath);
    if (!classPath.empty() && classPath.back() == '\r') classPath.pop_back();
    if (classPath.empty()) { std::cerr << "Missing test classpath\n"; return 2; }
    std::vector<std::string> values{"-Djava.class.path=" + classPath, "--enable-native-access=ALL-UNNAMED"};
    std::ifstream optionsFile{fs::path(argv[3])};
    if (!optionsFile) return 2;
    for (std::string line; std::getline(optionsFile, line);) {
        if (!line.empty() && line.back() == '\r') line.pop_back();
        if (!line.empty()) values.push_back(line);
    }
    std::vector<JavaVMOption> options;
    for (auto& value : values) options.push_back({value.data(), nullptr});
    JavaVMInitArgs setup{JNI_VERSION_1_8, static_cast<jint>(options.size()), options.data(), JNI_FALSE};
    JavaVM* vm = nullptr; JNIEnv* env = nullptr;
    if (createVm(&vm, reinterpret_cast<void**>(&env), &setup) != JNI_OK) return 2;
    jclass entry = env->FindClass("org/flatcam/fx/PlotBenchmarkNativeEntry");
    if (entry && !env->ExceptionCheck()) {
        jmethodID main = env->GetStaticMethodID(entry, "main", "([Ljava/lang/String;)V");
        if (main && !env->ExceptionCheck()) {
            jclass strings = env->FindClass("java/lang/String");
            if (strings && !env->ExceptionCheck()) {
                jobjectArray args = env->NewObjectArray(0, strings, nullptr);
                if (args && !env->ExceptionCheck()) env->CallStaticVoidMethod(entry, main, args);
            }
        }
    }
    bool failed = env->ExceptionCheck();
    if (failed) { env->ExceptionDescribe(); env->ExceptionClear(); }
    vm->DestroyJavaVM();
    return failed ? 1 : 0;
}
