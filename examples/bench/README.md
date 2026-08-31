# Neton 基准应用（Hyper4k 引擎）

TechEmpower 风格的框架级基准应用。路由与响应契约与 `neton` 仓库 `examples/bench`、
`neton-http-may4k` 仓库中的 bench 应用完全一致，唯一变量是 `http(...)` 的 Adapter。
完整规则见 `neton-docs` 的《HTTP 引擎能力规范》第九节。

## 构建与运行

```bash
# 在 neton-http-hyper4k 仓库根目录
./gradlew :examples:bench:linkReleaseExecutableMacosArm64

./examples/bench/build/bin/macosArm64/releaseExecutable/bench.kexe
```

## 压测

```bash
ab -k -c 256 -n 100000 http://127.0.0.1:8090/plaintext
ab -k -c 256 -n 100000 http://127.0.0.1:8090/json
```
