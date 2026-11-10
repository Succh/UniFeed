# Gitee Releases 同步配置

GitHub Release 发布后，工作流 `.github/workflows/publish-gitee.yml` 会把 `app-debug.apk` 同步到 Gitee 同名仓库。Secret 未配置时该工作流会跳过，不会失败。

按下面做完即可用 `workflow_dispatch` 回填已发布的版本（例如 v0.1.33）。

## 1. 注册 Gitee 并建仓库

1. 打开 [https://gitee.com](https://gitee.com) 注册 / 登录。
2. 右上角「+」→「新建仓库」。
3. 仓库名称填 `UniFeed`（与 GitHub 同名）。
4. 选择「公开」，不要勾选「使用 Readme 文件初始化仓库」（已有同名公开仓可跳过本步）。
5. 确认仓库地址为 [https://gitee.com/succh/UniFeed](https://gitee.com/succh/UniFeed)。

## 2. 生成有 releases 权限的私人令牌

1. 登录后点击右上角头像 →「设置」。
2. 左侧「安全设置」→「私人令牌」；直达：[https://gitee.com/profile/personal_access_tokens](https://gitee.com/profile/personal_access_tokens)。
3. 点「生成新令牌」。
4. 描述随便填，例如 `unifeed-github-actions`。
5. 权限至少勾选：
   - `projects`（仓库信息、发行版）
   - 若列表里有单独的 `releases` / 发行版相关项，一并勾选
6. 点「提交」，把生成的令牌复制下来。只显示一次，不要写进仓库、README 或 issue。

## 3. 在 GitHub 仓库写入 Secret

打开 GitHub 仓库 [https://github.com/Succh/UniFeed](https://github.com/Succh/UniFeed)：

1. 顶部「Settings」。
2. 左侧「Secrets and variables」→「Actions」。
3. 「New repository secret」，添加两条：

| Name | Value |
| --- | --- |
| `GITEE_TOKEN` | 上一步复制的私人令牌 |
| `GITEE_REPO` | `succh/UniFeed`（`owner/repo`，不要带 `https://`） |

不要把令牌写进工作流文件、README 或提交记录。

## 4. 手动回填已发布版本

Secret 配好后：

1. 打开 GitHub 仓库「Actions」。
2. 左侧选工作流「Publish APK to Gitee」。
3. 右侧「Run workflow」。
4. `tag` 填 GitHub 上已有的 tag，例如 `v0.1.33`。
5. 点「Run workflow」。

工作流会：

- 从 GitHub Release 下载 `app-debug.apk`
- 在 Gitee 创建同名 Release（已存在则复用）
- 若该 Release 还没有 `app-debug.apk` 附件则补传；已有则跳过

完成后直链为：

`https://gitee.com/succh/UniFeed/releases/download/<tag>/app-debug.apk`

例如：[https://gitee.com/succh/UniFeed/releases/download/v0.1.33/app-debug.apk](https://gitee.com/succh/UniFeed/releases/download/v0.1.33/app-debug.apk)

之后每次 GitHub Release 发布都会自动再同步一次。

## 5. 未配置 Secret 时的表现

`GITEE_TOKEN` 或 `GITEE_REPO` 缺失时，日志会写明原因并跳过后续步骤，job 仍为成功。配好 Secret 后再跑即可。
