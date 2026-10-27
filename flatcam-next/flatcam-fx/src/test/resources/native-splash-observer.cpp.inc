// Test-only instrumentation of windows belonging to THIS JNI host process.
// Keep the observer in physical coordinates even if AWT/Glass changes the process default.
#include <atomic>
#include <thread>
#include <chrono>
#include <map>

struct SplashObserver {
    std::atomic<bool> stop{false};
    bool changed=false;
    std::map<HWND,RECT> initial;
    static BOOL CALLBACK visit(HWND window, LPARAM data) {
        auto& observer=*reinterpret_cast<SplashObserver*>(data);
        DWORD process=0; GetWindowThreadProcessId(window,&process);
        if(process!=GetCurrentProcessId() || !IsWindowVisible(window)) return TRUE;
        wchar_t name[128]{}; GetClassNameW(window,name,128);
        if(std::wstring(name)!=L"SunAwtWindow") return TRUE;
        RECT bounds{}; if(!GetWindowRect(window,&bounds) || bounds.right-bounds.left<50 || bounds.bottom-bounds.top<50) return TRUE;
        auto [entry,created]=observer.initial.emplace(window,bounds);
        auto& first=entry->second;
        if(created) {
            std::cout << "NATIVE before: " << bounds.left << ',' << bounds.top << ' ' << bounds.right-bounds.left << 'x' << bounds.bottom-bounds.top << " dpi=" << GetDpiForWindow(window) << std::endl;
            auto marker=environment(L"FLATCAM_SPLASH_PROBE_READY");
            if(!marker.empty()) { std::ofstream signal(fs::path(marker),std::ios::binary); signal << "ready"; }
        } else if(std::abs(first.left-bounds.left)>1 || std::abs(first.top-bounds.top)>1
                || std::abs((first.right-first.left)-(bounds.right-bounds.left))>1
                || std::abs((first.bottom-first.top)-(bounds.bottom-bounds.top))>1) {
            if(!observer.changed) std::cout << "FAIL native splash moved/resized: " << bounds.left << ',' << bounds.top << ' ' << bounds.right-bounds.left << 'x' << bounds.bottom-bounds.top << " dpi=" << GetDpiForWindow(window) << std::endl;
            observer.changed=true;
        }
        return TRUE;
    }
    void watch() {
        auto previous=SetThreadDpiAwarenessContext(DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2);
        while(!stop) { EnumWindows(visit,reinterpret_cast<LPARAM>(this)); std::this_thread::sleep_for(std::chrono::milliseconds(5)); }
        if(previous) SetThreadDpiAwarenessContext(previous);
    }
};
