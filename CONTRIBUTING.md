# Contributing / 贡献指南

## Hard rule: keep the READMEs in sync / 硬性要求：README 中英文同步

`README.md` (English) and `README.zh-CN.md` (中文) **must always be updated together, in the same commit**. Never change one without the other. This applies to every change that touches features, requirements, build/install steps, limitations or layout. Pull requests that update only one language will not be merged.

`README.md`（英文）与 `README.zh-CN.md`（中文）**必须在同一次提交中同步更新**，不得只改其中一份。凡涉及功能、环境要求、构建/安装步骤、限制或目录结构的改动都适用。只更新单一语言的 PR 不会被合并。

## Other conventions / 其他约定
- Code, identifiers, commit messages and docs in `docs/`: English. / 代码、标识符、提交信息和 `docs/`：英文。
- Never commit signing keys (`*.jks`), phone backups, logs or built APKs. / 不要提交签名密钥、手机备份、日志或构建出的 APK。
- Package name: `com.benbenyu.btquicklaunch`.