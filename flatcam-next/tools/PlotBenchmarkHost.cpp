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

static jlong JNICALL counterNanos(JNIEnv*, jclass) {
    LARGE_INTEGER count{}, frequency{};
    QueryPerformanceCounter(&count); QueryPerformanceFrequency(&frequency);
    return static_cast<jlong>(static_cast<long double>(count.QuadPart) * 1000000000.L / frequency.QuadPart);
}
static jlong JNICALL counterFrequency(JNIEnv*, jclass) {
    LARGE_INTEGER frequency{}; QueryPerformanceFrequency(&frequency); return frequency.QuadPart;
}

int wmain(int argc, wchar_t** argv) {
    if (argc >= 2 && std::wstring(argv[1]) == L"--stop-presentmon") {
        if (argc != 4) return 2;
        wchar_t* end = nullptr;
        unsigned long target = wcstoul(argv[2], &end, 10);
        if (!target || !end || *end) return 2;
        HANDLE capture = OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION | SYNCHRONIZE, FALSE, target);
        if (!capture) return 3;
        struct CaptureHandle { HANDLE value; ~CaptureHandle() { CloseHandle(value); } } keep{capture};
        wchar_t executable[32768]{}; DWORD length = 32768;
        if (!QueryFullProcessImageNameW(capture, 0, executable, &length)
                || _wcsicmp(executable, argv[3]) != 0 || WaitForSingleObject(capture, 0) != WAIT_TIMEOUT) return 3;
        // A separate helper may signal only an isolated capture console, never
        // the user's PowerShell/application console. Refuse if anyone else is attached.
        FreeConsole();
        if (!AttachConsole(target)) return 3;
        DWORD members[16]{};
        DWORD count = GetConsoleProcessList(members, 16);
        if (!count || count > 16) return 3;
        bool found = false;
        for (DWORD i = 0; i < count; ++i) {
            if (members[i] == target) found = true;
            else if (members[i] != GetCurrentProcessId()) return 3;
        }
        if (!found || WaitForSingleObject(capture, 0) != WAIT_TIMEOUT || !SetConsoleCtrlHandler(nullptr, TRUE)) return 3;
        if (!GenerateConsoleCtrlEvent(CTRL_C_EVENT, 0)) return 3;
        Sleep(100);
        return 0;
    }
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
        JNINativeMethod methods[] = {
            {const_cast<char*>("counterNanos"), const_cast<char*>("()J"), reinterpret_cast<void*>(counterNanos)},
            {const_cast<char*>("counterFrequency"), const_cast<char*>("()J"), reinterpret_cast<void*>(counterFrequency)}
        };
        if (env->RegisterNatives(entry, methods, 2) != JNI_OK) {
            env->ExceptionDescribe(); env->ExceptionClear(); vm->DestroyJavaVM(); return 1;
        }
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
