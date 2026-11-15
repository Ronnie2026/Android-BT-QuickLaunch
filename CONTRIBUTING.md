# Contributing / 贡献指南

## Read before development / 开发前必读

Before planning or editing, read [AGENTS.md](AGENTS.md), this guide, [project context](docs/PROJECT_CONTEXT.md), [architecture](docs/ARCHITECTURE.md), and [current status](docs/STATUS.md), then the relevant feature documentation. The corresponding Chinese files are equivalent reading options. Inspect current code and local changes before implementation. If code and docs disagree, verify the facts and synchronize both languages.

制定方案或修改项目前，必须先阅读 [AGENTS.md](AGENTS.md)、本规范、[项目背景](docs/PROJECT_CONTEXT.zh-CN.md)、[架构](docs/ARCHITECTURE.zh-CN.md)和[当前状态](docs/STATUS.zh-CN.md)，再阅读相关功能文档。中英文版本均可作为阅读入口。实现前检查当前代码与本地改动；若代码与说明不一致，核实事实并同步修正文档，避免需求偏差。

## Hard rule: bilingual docs / 硬性要求：Docs 中英文同步

Every maintained Markdown document in `docs/` must have an English default file `NAME.md` and a separate Simplified Chinese file `NAME.zh-CN.md`. Both must link to each other. Create, update, rename, or delete the pair in the same commit, including changes to behavior, examples, validation results and limitations. Update project context when architecture, supported apps, matching rules, requirements or reliability boundaries change. Translation must preserve technical meaning and distinguish planned work from shipped behavior.

`docs/` 中每份维护中的 Markdown 文档必须提供默认英文版 `NAME.md` 与独立简体中文版 `NAME.zh-CN.md`，并相互链接。新增、修改、重命名和删除必须成对出现在同一提交中，行为、示例、验证结果与限制均须同步。架构、支持的应用、识别规则、需求或可靠性边界发生变化时，必须更新项目背景。两种语言含义须一致，明确区分已实现功能与后续计划。

## Hard rule: keep the READMEs in sync / 硬性要求：README 中英文同步

`README.md` (English) and `README.zh-CN.md` (中文) **must always be updated together, in the same commit**. Never change one without the other. This applies to every change that touches features, requirements, build/install steps, limitations or layout. Pull requests that update only one language will not be merged.

`README.md`（英文）与 `README.zh-CN.md`（中文）**必须在同一次提交中同步更新**，不得只改其中一份。凡涉及功能、环境要求、构建/安装步骤、限制或目录结构的改动都适用。只更新单一语言的 PR 不会被合并。

## Other conventions / 其他约定
- Code, identifiers and commit messages: English. Documentation defaults to English with a synchronized Simplified Chinese counterpart. / 代码、标识符和提交信息使用英文；文档默认英文，并同步提供独立简体中文版。
- Never commit signing keys (`*.jks`), phone backups, logs or built APKs. / 不要提交签名密钥、手机备份、日志或构建出的 APK。
- Package name: `com.benbenyu.btquicklaunch`.
