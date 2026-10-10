# ====================================================================
# TagPlayer 一键构建安装脚本 (build_install.py)
#
# 【功能】
#   1. 调用 Gradle 构建 APK，默认只构建 normal 风味的 release 包
#   2. 实时输出带时间戳的构建日志，并自动监控构建是否完成（成功/失败判定）
#   3. 构建成功后自动通过 adb 安装到 USB 连接的手机上
#   4. 支持构建 foss / google 另外两种风味，也支持构建 debug 包
#   5. 支持只安装不构建（--install-only），直接安装之前已构建好的包
#   6. 单实例运行：已有实例在构建时，新实例会直接退出，避免并发构建冲突
#   7. 统一按 UTF-8 输出日志，自动兼容 Gradle/adb 的 UTF-8 与 GBK 编码，
#      解决 Windows 控制台中文乱码问题
#
# 【用法】
#   python build_install.py [选项]
#
# 【选项】
#   --flavor {normal,foss,google,all} [风味2 ...]
#                         要构建的风味，默认 normal；
#                         可一次指定多个，all 等价于三种风味全构建
#   --debug               构建 debug 包（默认构建 release 包）
#   --no-install          只构建，不安装到手机
#   --install-only        跳过构建，直接安装之前已构建好的包
#                         （安装哪种风味/类型仍由 --flavor、--debug 决定）
#   -h, --help            显示帮助信息
#
# 【安装说明】
#   只会安装 --flavor 列表中的第一个风味（默认 normal），避免多个风味互相覆盖。
#
# 【示例】
#   python build_install.py                          # 构建 normal release 并安装
#   python build_install.py --flavor foss            # 构建 foss release 并安装
#   python build_install.py --flavor all             # 三种风味 release 全构建，安装 normal
#   python build_install.py --debug                  # 构建 normal debug 并安装
#   python build_install.py --flavor google --debug  # 构建 google debug 并安装
#   python build_install.py --no-install             # 只构建 normal release，不安装
#   python build_install.py --install-only           # 不构建，直接安装已构建的 normal release
#   python build_install.py --install-only --debug   # 不构建，直接安装已构建的 normal debug
#
# 【APK 输出目录】
#   app/build/outputs/apk/<风味>/<release|debug>/
# ====================================================================

import argparse
import os
import subprocess
import sys
import tempfile
import time

try:
    import msvcrt  # Windows 单实例文件锁
except ImportError:
    msvcrt = None

# ------------------------- 项目常量 -------------------------
PROJECT_ROOT = os.path.dirname(os.path.abspath(__file__))
GRADLEW = os.path.join(PROJECT_ROOT, "gradlew.bat")
APK_OUTPUT_ROOT = os.path.join(PROJECT_ROOT, "app", "build", "outputs", "apk")
ADB_CANDIDATES = [
    r"C:\Users\Administrator\AppData\Local\Android\Sdk\platform-tools\adb.exe",
    "adb",
]
FLAVORS = ["normal", "foss", "google"]

_lock_file = None  # 持有单实例锁的文件句柄，进程存活期间不释放


# ------------------------- 日志输出 -------------------------
def setup_console():
    """统一控制台编码为 UTF-8，避免中文/特殊字符输出报错。"""
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8", errors="replace")
        except Exception:
            pass


def log(message=""):
    print("[{}] {}".format(time.strftime("%H:%M:%S"), message), flush=True)


def decode_line(raw):
    """子进程输出按 UTF-8 优先、GBK 兜底解码，兼容不同工具的编码。"""
    if isinstance(raw, str):
        return raw.rstrip("\r\n")
    for encoding in ("utf-8", "gbk"):
        try:
            return raw.decode(encoding).rstrip("\r\n")
        except UnicodeDecodeError:
            continue
    return raw.decode("utf-8", errors="replace").rstrip("\r\n")


# ------------------------- 单实例锁 -------------------------
def acquire_lock():
    """保证同一时间只有一个本脚本实例在运行。"""
    global _lock_file
    lock_path = os.path.join(tempfile.gettempdir(), "tagplayer_build_install.lock")
    _lock_file = open(lock_path, "a+")
    if msvcrt is not None:
        try:
            _lock_file.seek(0)
            msvcrt.locking(_lock_file.fileno(), msvcrt.LK_NBLCK, 1)
        except OSError:
            _lock_file.close()
            _lock_file = None
            print("已有实例正在运行，本实例退出。")
            sys.exit(1)
    else:
        # 非 Windows 环境退化为 PID 文件检查
        _lock_file.seek(0)
        pid_text = _lock_file.read().strip()
        if pid_text:
            try:
                os.kill(int(pid_text), 0)
                _lock_file.close()
                _lock_file = None
                print("已有实例正在运行，本实例退出。")
                sys.exit(1)
            except (ValueError, ProcessLookupError, PermissionError):
                pass
        _lock_file.seek(0)
        _lock_file.truncate()
        _lock_file.write(str(os.getpid()))
        _lock_file.flush()


# ------------------------- 子进程执行与监控 -------------------------
def run_logged(cmd, watch_build=False):
    """
    执行命令并逐行实时输出日志。
    返回 (returncode, ok)；watch_build=True 时根据 BUILD SUCCESSFUL / BUILD FAILED
    等标记判定构建是否完成。
    """
    log("$ " + " ".join(cmd))
    start = time.time()
    build_result = None
    try:
        proc = subprocess.Popen(
            cmd,
            cwd=PROJECT_ROOT,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
        )
        for raw in proc.stdout:
            line = decode_line(raw)
            log(line)
            if watch_build:
                if "BUILD SUCCESSFUL" in line:
                    build_result = True
                elif "BUILD FAILED" in line or "FAILURE:" in line:
                    build_result = False
        returncode = proc.wait()
    except KeyboardInterrupt:
        log("检测到手动中断，正在停止子进程...")
        try:
            proc.kill()
        except Exception:
            pass
        sys.exit(130)
    except FileNotFoundError as exc:
        log("命令不存在: {}".format(exc))
        return 1, False

    elapsed = time.time() - start
    if watch_build:
        if build_result is True:
            log("构建完成（耗时 {:.1f} 秒）".format(elapsed))
            return returncode, True
        log("构建失败（耗时 {:.1f} 秒，returncode={}）".format(elapsed, returncode))
        return returncode, False
    return returncode, returncode == 0


def run_capture(cmd):
    """执行命令，收集全部输出行，不打印。"""
    try:
        proc = subprocess.Popen(
            cmd, cwd=PROJECT_ROOT, stdout=subprocess.PIPE, stderr=subprocess.STDOUT
        )
        lines = [decode_line(raw) for raw in proc.stdout]
        return proc.wait(), lines
    except FileNotFoundError as exc:
        return 1, ["命令不存在: {}".format(exc)]


# ------------------------- 构建与安装 -------------------------
def find_adb():
    for candidate in ADB_CANDIDATES:
        if os.path.isabs(candidate):
            if os.path.isfile(candidate):
                return candidate
        else:
            return candidate  # 交给系统 PATH 查找
    return "adb"


def check_device(adb):
    returncode, lines = run_capture([adb, "devices"])
    devices = [
        line.split("\t")[0]
        for line in lines[1:]
        if "\tdevice" in line and not line.startswith("List")
    ]
    if returncode != 0 or not devices:
        log("未检测到已连接的手机，请检查：")
        log("  1) 手机已用 USB 数据线连接电脑；")
        log("  2) 已开启开发者选项 -> USB 调试；")
        log("  3) 已在手机上允许本电脑的调试授权。")
        for line in lines:
            log("  " + line)
        return None
    log("已连接设备: {}".format(", ".join(devices)))
    return devices[0]


def find_newest_apk(flavor, build_type):
    out_dir = os.path.join(APK_OUTPUT_ROOT, flavor, build_type)
    if not os.path.isdir(out_dir):
        return None
    apks = [
        os.path.join(out_dir, name)
        for name in os.listdir(out_dir)
        if name.lower().endswith(".apk")
    ]
    if not apks:
        return None
    newest = max(apks, key=os.path.getmtime)
    log(
        "找到 APK: {} ({} MB, {})".format(
            newest,
            round(os.path.getsize(newest) / 1024 / 1024, 2),
            time.strftime("%Y-%m-%d %H:%M:%S", time.localtime(os.path.getmtime(newest))),
        )
    )
    return newest


def build(flavors, build_type):
    # 形如 assembleNormalRelease / assembleFossDebug
    tasks = [
        "assemble{}{}".format(flavor.capitalize(), build_type.capitalize())
        for flavor in flavors
    ]
    log("开始构建: 风味={} 类型={}".format(",".join(flavors), build_type))
    if not os.path.isfile(GRADLEW):
        log("找不到 gradlew.bat: {}".format(GRADLEW))
        return False
    returncode, ok = run_logged(
        ["cmd", "/c", GRADLEW] + tasks + ["--console=plain"], watch_build=True
    )
    return ok and returncode == 0


def install(flavor, build_type):
    apk = find_newest_apk(flavor, build_type)
    if apk is None:
        log("未在输出目录中找到 {} {} 的 APK，跳过安装。".format(flavor, build_type))
        return False
    adb = find_adb()
    if check_device(adb) is None:
        return False
    log("开始安装: {}".format(os.path.basename(apk)))
    returncode, lines = run_capture([adb, "install", "-r", apk])
    for line in lines:
        log(line)
    if returncode == 0 and any(line.strip() == "Success" for line in lines):
        log("安装成功。")
        return True
    log("安装失败（returncode={}）。".format(returncode))
    log("提示: 若报签名冲突/incremental 等问题，可先卸载旧应用再重装。")
    return False


# ------------------------- 入口 -------------------------
def main():
    setup_console()
    acquire_lock()

    parser = argparse.ArgumentParser(
        description="TagPlayer 构建安装脚本：构建 APK 并自动安装到手机。",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=(
            "示例:\n"
            "  python build_install.py\n"
            "  python build_install.py --flavor foss\n"
            "  python build_install.py --flavor all\n"
            "  python build_install.py --debug\n"
            "  python build_install.py --flavor google --debug\n"
            "  python build_install.py --no-install\n"
            "  python build_install.py --install-only\n"
            "  python build_install.py --install-only --debug\n"
        ),
    )
    parser.add_argument(
        "--flavor",
        nargs="+",
        choices=FLAVORS + ["all"],
        default=["normal"],
        help="要构建的风味，可多选，all 表示全部三种（默认 normal）",
    )
    parser.add_argument("--debug", action="store_true", help="构建 debug 包（默认 release）")
    mode_group = parser.add_mutually_exclusive_group()
    mode_group.add_argument(
        "--no-install", action="store_true", help="只构建，不安装到手机"
    )
    mode_group.add_argument(
        "--install-only",
        action="store_true",
        help="跳过构建，直接安装之前已构建好的包",
    )
    args = parser.parse_args()

    flavors = FLAVORS if "all" in args.flavor else list(dict.fromkeys(args.flavor))
    build_type = "debug" if args.debug else "release"

    log("=" * 60)
    log("TagPlayer 构建安装脚本启动")
    log("工作目录: {}".format(PROJECT_ROOT))
    if args.install_only:
        log("模式: 只安装（跳过构建），将使用之前已构建的 {} 包".format(build_type))
    else:
        log("构建目标: {} 包, 风味: {}".format(build_type, ", ".join(flavors)))
    log("=" * 60)

    if not args.install_only:
        if not build(flavors, build_type):
            log("构建未成功，流程终止。")
            sys.exit(1)

        if args.no_install:
            log("已指定 --no-install，跳过安装。")
            return

    target = flavors[0]
    log("准备安装风味: {}（只安装第一个选定的风味）".format(target))
    if not install(target, build_type):
        sys.exit(2)

    log("=" * 60)
    if args.install_only:
        log("全部完成: {} {} 包安装成功".format(target, build_type))
    else:
        log("全部完成: {} {} 构建并安装成功".format(target, build_type))
    log("=" * 60)


if __name__ == "__main__":
    main()
