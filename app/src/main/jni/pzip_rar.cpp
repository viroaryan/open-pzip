// pzip RAR bridge: thin JNI layer over the UnRAR DLL API (RAR 1.5 - RAR 7).
//
// UnRAR source code may be used in any software to handle RAR archives
// without limitations free of charge, but cannot be used to develop RAR
// (WinRAR) compatible archiver and to re-create RAR compression algorithm,
// which is proprietary. Distribution of modified UnRAR source code in separate
// form or as a part of other software is permitted, provided that full text of
// this paragraph, starting from "UnRAR source code" words, is included in
// license, or in documentation if license is not available, and in source code
// comments of resulting package.

#include <jni.h>
#include <string>
#include <vector>
#include <cstring>
#include <algorithm>
#include "unrar/raros.hpp"
#include "unrar/dll.hpp"

namespace {

constexpr int MODE_LIST = 0;
constexpr int MODE_EXTRACT = 1;
constexpr int MODE_TEST = 2;

// Error code used when the Kotlin side cancels the operation.
constexpr int PZIP_CANCELLED = 1000;

struct Ctx {
  JNIEnv *env;
  jobject cb;
  jmethodID onData;
  jmethodID needPassword;
  bool cancelled = false;
  bool passwordAsked = false;
};

std::wstring toWide(JNIEnv *env, jstring s) {
  std::wstring out;
  if (s == nullptr) return out;
  const jsize len = env->GetStringLength(s);
  const jchar *chars = env->GetStringChars(s, nullptr);
  for (jsize i = 0; i < len; i++) {
    uint32_t c = chars[i];
    if (c >= 0xD800 && c <= 0xDBFF && i + 1 < len) {
      uint32_t lo = chars[i + 1];
      if (lo >= 0xDC00 && lo <= 0xDFFF) {
        c = 0x10000 + ((c - 0xD800) << 10) + (lo - 0xDC00);
        i++;
      }
    }
    out.push_back(static_cast<wchar_t>(c));
  }
  env->ReleaseStringChars(s, chars);
  return out;
}

jstring fromWide(JNIEnv *env, const wchar_t *w) {
  std::vector<jchar> buf;
  for (; *w != 0; w++) {
    uint32_t c = static_cast<uint32_t>(*w);
    if (c >= 0x10000) {
      c -= 0x10000;
      buf.push_back(static_cast<jchar>(0xD800 + (c >> 10)));
      buf.push_back(static_cast<jchar>(0xDC00 + (c & 0x3FF)));
    } else {
      buf.push_back(static_cast<jchar>(c));
    }
  }
  return env->NewString(buf.data(), static_cast<jsize>(buf.size()));
}

int CALLBACK rarCallback(UINT msg, LPARAM userData, LPARAM p1, LPARAM p2) {
  auto *ctx = reinterpret_cast<Ctx *>(userData);
  JNIEnv *env = ctx->env;
  switch (msg) {
    case UCM_PROCESSDATA: {
      jboolean go = env->CallBooleanMethod(ctx->cb, ctx->onData, static_cast<jint>(p2));
      if (env->ExceptionCheck() || !go) {
        ctx->cancelled = true;
        return -1;
      }
      return 1;
    }
    case UCM_NEEDPASSWORDW: {
      auto pw = static_cast<jstring>(env->CallObjectMethod(ctx->cb, ctx->needPassword));
      ctx->passwordAsked = true;
      if (env->ExceptionCheck() || pw == nullptr) return -1;
      std::wstring w = toWide(env, pw);
      env->DeleteLocalRef(pw);
      auto *dst = reinterpret_cast<wchar_t *>(p1);
      size_t max = static_cast<size_t>(p2);
      if (max == 0) return -1;
      size_t n = w.size() < max - 1 ? w.size() : max - 1;
      wmemcpy(dst, w.data(), n);
      dst[n] = 0;
      // Wipe our temporary copy of the password.
      std::fill(w.begin(), w.end(), L'\0');
      return 1;
    }
    case UCM_NEEDPASSWORD:
      return -1;  // Only the wide variant is supported.
    case UCM_CHANGEVOLUMEW:
    case UCM_CHANGEVOLUME:
      // RAR_VOL_ASK means the next volume is missing.
      return p2 == RAR_VOL_ASK ? -1 : 1;
    case UCM_LARGEDICT:
      // Refuse dictionaries above the default limit to protect phone memory.
      return 0;
    default:
      return 0;
  }
}

}  // namespace

extern "C" JNIEXPORT jint JNICALL
Java_app_pzip_core_archive_RarNative_run(JNIEnv *env, jclass, jstring jpath, jint mode, jobject cb) {
  jclass cls = env->GetObjectClass(cb);
  jmethodID onArchive = env->GetMethodID(cls, "onArchive", "(I)V");
  jmethodID onHeader = env->GetMethodID(cls, "onHeader",
      "(Ljava/lang/String;JJJIII)Ljava/lang/String;");
  Ctx ctx{env, cb,
          env->GetMethodID(cls, "onData", "(I)Z"),
          env->GetMethodID(cls, "needPassword", "()Ljava/lang/String;")};

  std::wstring path = toWide(env, jpath);
  RAROpenArchiveDataEx open{};
  open.ArcNameW = const_cast<wchar_t *>(path.c_str());
  open.OpenMode = mode == MODE_LIST ? RAR_OM_LIST : RAR_OM_EXTRACT;
  open.Callback = rarCallback;
  open.UserData = reinterpret_cast<LPARAM>(&ctx);

  HANDLE h = RAROpenArchiveEx(&open);
  if (h == nullptr || open.OpenResult != ERAR_SUCCESS) {
    if (h != nullptr) RARCloseArchive(h);
    return open.OpenResult != 0 ? static_cast<jint>(open.OpenResult) : ERAR_EOPEN;
  }
  env->CallVoidMethod(cb, onArchive, static_cast<jint>(open.Flags));
  if (env->ExceptionCheck()) {
    RARCloseArchive(h);
    return PZIP_CANCELLED;
  }

  auto *hd = new RARHeaderDataEx();
  int result = ERAR_SUCCESS;
  while (true) {
    memset(hd, 0, sizeof(RARHeaderDataEx));
    int rh = RARReadHeaderEx(h, hd);
    if (rh == ERAR_END_ARCHIVE) break;
    if (rh != ERAR_SUCCESS) {
      result = rh;
      break;
    }
    const jlong unp = (static_cast<jlong>(hd->UnpSizeHigh) << 32) | hd->UnpSize;
    const jlong pack = (static_cast<jlong>(hd->PackSizeHigh) << 32) | hd->PackSize;
    const uint64_t ft = (static_cast<uint64_t>(hd->MtimeHigh) << 32) | hd->MtimeLow;
    const jlong mtime = ft == 0 ? 0 : static_cast<jlong>(ft / 10000ULL) - 11644473600000LL;

    jstring name = fromWide(env, hd->FileNameW);
    auto dest = static_cast<jstring>(env->CallObjectMethod(cb, onHeader, name, unp, pack, mtime,
        static_cast<jint>(hd->Flags), static_cast<jint>(hd->FileCRC), static_cast<jint>(hd->Method)));
    env->DeleteLocalRef(name);
    if (env->ExceptionCheck()) {
      result = PZIP_CANCELLED;
      break;
    }

    int pr;
    if (mode == MODE_LIST || dest == nullptr) {
      pr = RARProcessFileW(h, RAR_SKIP, nullptr, nullptr);
    } else if (mode == MODE_TEST) {
      pr = RARProcessFileW(h, RAR_TEST, nullptr, nullptr);
    } else {
      std::wstring destW = toWide(env, dest);
      pr = RARProcessFileW(h, RAR_EXTRACT, nullptr, const_cast<wchar_t *>(destW.c_str()));
    }
    if (dest != nullptr) env->DeleteLocalRef(dest);
    if (ctx.cancelled) {
      result = PZIP_CANCELLED;
      break;
    }
    if (pr != ERAR_SUCCESS) {
      result = pr;
      break;
    }
  }
  delete hd;
  RARCloseArchive(h);
  return result;
}
