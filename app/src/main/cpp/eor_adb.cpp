/* Copyright 2017-2025 Rikka contributors; 2026 eOr contributors. Apache-2.0. */
#include <jni.h>
#include <openssl/aead.h>
#include <openssl/evp.h>
#include <openssl/hkdf.h>
#include <openssl/curve25519.h>
#include <android/log.h>
#include <sys/system_properties.h>
#include <cstdlib>
#include <cstring>

struct PairingContext {
    SPAKE2_CTX *spake = nullptr;
    EVP_AEAD_CTX *aead = nullptr;
    uint8_t msg[SPAKE2_MAX_MSG_SIZE]{};
    size_t msg_size = 0;
    uint64_t enc = 0, dec = 0;
};
#define EOR_LOGE(msg) __android_log_write(ANDROID_LOG_ERROR, "EorBroker", msg)

static jlong create(JNIEnv *env, jclass, jboolean client, jbyteArray password) {
    auto *ctx = new PairingContext();
    const uint8_t client_name[] = "adb pair client";
    const uint8_t server_name[] = "adb pair server";
    ctx->spake = SPAKE2_CTX_new(client ? spake2_role_alice : spake2_role_bob,
                                client_name, sizeof(client_name), server_name, sizeof(server_name));
    if (!ctx->spake) {
        EOR_LOGE("pairing/native/create-context");
        delete ctx;
        return 0;
    }
    jsize n = env->GetArrayLength(password);
    jbyte *p = env->GetByteArrayElements(password, nullptr);
    int ok = SPAKE2_generate_msg(ctx->spake, ctx->msg, &ctx->msg_size,
                                 sizeof(ctx->msg), reinterpret_cast<uint8_t *>(p), n);
    env->ReleaseByteArrayElements(password, p, JNI_ABORT);
    if (!ok || ctx->msg_size == 0) {
        EOR_LOGE("pairing/native/generate-message");
        SPAKE2_CTX_free(ctx->spake);
        delete ctx;
        return 0;
    }
    return reinterpret_cast<jlong>(ctx);
}

static jbyteArray message(JNIEnv *env, jobject, jlong ptr) {
    auto *ctx = reinterpret_cast<PairingContext *>(ptr);
    auto out = env->NewByteArray(ctx->msg_size);
    env->SetByteArrayRegion(out, 0, ctx->msg_size, reinterpret_cast<jbyte *>(ctx->msg));
    return out;
}

static jboolean init(JNIEnv *env, jobject, jlong ptr, jbyteArray peer) {
    auto *ctx = reinterpret_cast<PairingContext *>(ptr);
    jsize n = env->GetArrayLength(peer);
    if (n > SPAKE2_MAX_MSG_SIZE) return JNI_FALSE;
    jbyte *p = env->GetByteArrayElements(peer, nullptr);
    uint8_t material[SPAKE2_MAX_KEY_SIZE];
    size_t material_size = 0;
    int ok = SPAKE2_process_msg(ctx->spake, material, &material_size, sizeof(material),
                                reinterpret_cast<uint8_t *>(p), n);
    env->ReleaseByteArrayElements(peer, p, JNI_ABORT);
    if (!ok) {
        EOR_LOGE("pairing/native/process-peer");
        return JNI_FALSE;
    }
    uint8_t key[16];
    const uint8_t info[] = "adb pairing_auth aes-128-gcm key";
    if (!HKDF(key, sizeof(key), EVP_sha256(), material, material_size, nullptr, 0, info,
              sizeof(info) - 1)) {
        EOR_LOGE("pairing/native/hkdf");
        return JNI_FALSE;
    }
    ctx->aead = EVP_AEAD_CTX_new(EVP_aead_aes_128_gcm(), key, sizeof(key),
                                 EVP_AEAD_DEFAULT_TAG_LENGTH);
    if (!ctx->aead) EOR_LOGE("pairing/native/aead");
    return ctx->aead ? JNI_TRUE : JNI_FALSE;
}

static jbyteArray crypt(JNIEnv *env, PairingContext *ctx, jbyteArray input, bool encrypt) {
    jsize n = env->GetArrayLength(input);
    if (n <= 0 || n > 16384) return nullptr;
    jbyte *p = env->GetByteArrayElements(input, nullptr);
    size_t cap = encrypt ? n + EVP_AEAD_max_overhead(EVP_AEAD_CTX_aead(ctx->aead)) : n;
    auto *buf = new uint8_t[cap];
    uint8_t nonce[12]{};
    uint64_t &seq = encrypt ? ctx->enc : ctx->dec;
    memcpy(nonce, &seq, sizeof(seq));
    size_t written = 0;
    int ok = encrypt
             ? EVP_AEAD_CTX_seal(ctx->aead, buf, &written, cap, nonce, sizeof(nonce),
                                 reinterpret_cast<uint8_t *>(p), n, nullptr, 0)
             : EVP_AEAD_CTX_open(ctx->aead, buf, &written, cap, nonce, sizeof(nonce),
                                 reinterpret_cast<uint8_t *>(p), n, nullptr, 0);
    env->ReleaseByteArrayElements(input, p, JNI_ABORT);
    if (!ok) {
        EOR_LOGE(encrypt ? "pairing/native/encrypt" : "pairing/native/decrypt");
        delete[] buf;
        return nullptr;
    }
    ++seq;
    auto out = env->NewByteArray(written);
    env->SetByteArrayRegion(out, 0, written, reinterpret_cast<jbyte *>(buf));
    delete[] buf;
    return out;
}

static jbyteArray encrypt(JNIEnv *e, jobject, jlong p, jbyteArray b) {
    return crypt(e, reinterpret_cast<PairingContext *>(p), b, true);
}

static jbyteArray decrypt(JNIEnv *e, jobject, jlong p, jbyteArray b) {
    return crypt(e, reinterpret_cast<PairingContext *>(p), b, false);
}

static void destroy(JNIEnv *, jobject, jlong p) {
    auto *c = reinterpret_cast<PairingContext *>(p);
    if (!c)return;
    if (c->spake)SPAKE2_CTX_free(c->spake);
    if (c->aead)EVP_AEAD_CTX_free(c->aead);
    delete c;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_gamelaunch_frontend_systemui_EorAdbNative_tlsPort(JNIEnv *, jobject) {
    char value[PROP_VALUE_MAX]{};
    if (__system_property_get("service.adb.tls.port", value) <= 0) return 0;
    char *end = nullptr;
    const long port = strtol(value, &end, 10);
    return end != value && *end == '\0' && port > 0 && port <= 65535
           ? static_cast<jint>(port) : 0;
}

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *) {
    JNIEnv *e = nullptr;
    if (vm->GetEnv(reinterpret_cast<void **>(&e), JNI_VERSION_1_6) != JNI_OK)return -1;
    JNINativeMethod m[] = {{const_cast<char *>("nativeCreate"),  const_cast<char *>("(Z[B)J"),  (void *) create},
                           {const_cast<char *>("nativeMessage"), const_cast<char *>("(J)[B"),   (void *) message},
                           {const_cast<char *>("nativeInit"),    const_cast<char *>("(J[B)Z"),  (void *) init},
                           {const_cast<char *>("nativeEncrypt"), const_cast<char *>("(J[B)[B"), (void *) encrypt},
                           {const_cast<char *>("nativeDecrypt"), const_cast<char *>("(J[B)[B"), (void *) decrypt},
                           {const_cast<char *>("nativeDestroy"), const_cast<char *>("(J)V"),    (void *) destroy}};
    return e->RegisterNatives(e->FindClass("com/gamelaunch/frontend/systemui/AdbPairingContext"), m,
                              6) == 0 ? JNI_VERSION_1_6 : -1;
}
