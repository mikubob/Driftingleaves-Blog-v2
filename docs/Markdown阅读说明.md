# Markdown 正文与阅读展示

后台文章编写使用 MdEditor 6.3.1；博客正式文章、后台已保存内容预览和文章列表详情预览使用 MdPreview 6.3.1。保存只提交 `markdownContent` 和既有版本信息，不提交前端生成的 HTML 或主题。后端继续使用 CommonMark 0.31.0 + jsoup 1.23.2 生成并清理 HTML，供 RSS 使用；不信任数据库旧 HTML 或客户端生成的 HTML。

## 排版与明暗

博客阅读主题使用项目已有 Element Plus 选择器，位于正文上方右侧，颜色、圆角、下拉菜单和焦点状态跟随网站主题。

两端提供 `default`、`github`、`vuepress`、`mk-cute`、`smart-blue`、`cyanosis` 六种 `previewTheme`。作者编辑与已保存预览共用本机 `dl-author-markdown-theme`，读者使用独立的 `dl-reader-markdown-theme`，默认 github。偏好只保存在当前浏览器的当前来源，不写入数据库或文章，不跨设备同步，也不强制另一方使用作者的选择。浏览器禁用存储时本页仍能切换。

`previewTheme` 决定排版，`theme` 决定 light/dark。博客正文只跟随网站现有黑白天开关和 `useThemeStore` 的 auto/light/dark 机制，不另设正文的明暗按钮。管理端编辑器和所有文章预览固定 `theme="light"`，不提供明暗按钮，也不读取或保存作者明暗偏好。切换排版直接更新组件属性，不重建编辑器、不清除未保存输入。正文背景透明并融入文章卡片，正文文字跟随网站配色；保留六种主题的标题和装饰差异。夜间降低 mk-cute 网格对比度，smart-blue 的深色标题和链接补充浅蓝色。

## 安全、附件与阅读交互

网页解析结果始终经过 `Front/shared/previewSanitizer.mjs` 的专用 xss 白名单：保留代码工具栏、行号、同步滚动标记、必要 class、标题 id 和静态 SVG/circle/path；清理脚本、事件、样式和危险链接。后台 `editorPreview.mjs` 的回归检查继续使用同一清理器，不能关闭清理或退回默认白名单。原始 HTML 开头不跳过 Markdown 解析。

网页保留语言标识、高亮、复制、details 折叠、中文重复标题去重、桌面/手机目录和片段导航。复制使用解析器取得的代码原文，保留首尾换行；浏览器不允许剪贴板时显示失败提示并允许手动选择。复制需要 HTTPS 或 localhost 安全上下文。高亮使用本地 highlight.js 11.11.1，`markdown-code.css` 显式提供共用的 Atom 风格配色，不依赖高亮 CDN，也不提供额外的代码主题选择器。md-editor-v3 6.3.1 在提供 highlighter 实例后会跳过自动 CSS 加载，因此仅有高亮标记还不够，验证时同时检查实际关键字颜色。

长代码在代码容器内横向滚动，宽表格局部滚动，图片受正文容器约束。标题 id 使用共享 Unicode 标题规则，按文档顺序为重复标题追加后缀。博客沿用既有目录、锚点和固定导航偏移。

正文和封面的受管附件继续经过后端 `ArticleAttachmentPolicy` 检查，不使用 OSS 直链。博客受管附件指向配置的 API 来源。后台编辑实时预览及已保存预览将受管图片转为后台文章附件路径，通过 Bearer API 读取 PNG/JPEG 并生成临时对象 URL；切换原文和卸载时取消请求、释放 URL，已取消请求的迟到结果同样释放。图片读取失败显示错误，不回退到公开直链。

评论、留言、经历等已有 SafeHtml 消费者继续展示后端安全 HTML。`enhanceHtml.mjs` 与 `reading-content.css` 保留给这些消费者。文章统一使用 MdPreview，普通留言使用后端安全 HTML。

## 接口

文章详情统一返回 `markdownContent:string`，不返回 HTML 正文。列表不返回正文。Markdown 在文章锁定、PUBLIC 检查及附件检查之后返回；网页请求边界要求该字段存在且类型正确，空字符串是有效原文。公开条件、阅读计数和目录交互保留。RSS 独立生成并清理 HTML。真实字段见 [API](API.md#response-articlepublicdetail) 和 [API_CONTRACT](API_CONTRACT.md)。

## 语法对齐与差异

网页使用 md-editor-v3 内置 markdown-it，后端使用 CommonMark，两套解析器不能保证所有输入完全一致。两端共同支持基本段落、标题、链接、图片、围栏/缩进代码、表格和任务列表；网页关闭普通单换行的强制换行，关闭删除线及 sub/sup/admonition 扩展，并禁用 Mermaid、KaTeX、ECharts，避免把网页特有语法误当成 RSS 已支持功能。

仍存在的差异：

- 自动链接分别使用 markdown-it linkify 与 CommonMark autolink 扩展，边界输入的识别结果可能不同。
- 网页保留排版类、工具栏静态 SVG 和 details；后端 jsoup 使用更严格的正文白名单，会去掉这些展示标记，RSS 不包含网页控件。
- 文章 Markdown 按声明语言高亮，未知或未声明语言由本地 highlighter 自动识别；其他 SafeHtml 消费者仍只高亮声明且已支持的语言。文章代码展示会裁掉首尾空行，但复制保留原有代码空白。
- 原始 HTML、复杂嵌套、标题内 HTML/实体及表格边界可能产生不同的结构或标题文本；相同可见标题使用同一去重规则，不承诺不同解析结构下的锚点完全相同。
- 字数和阅读分钟始终由后端安全正文计算，不按前端显示内容重新入库。

RSS 保留摘要 `description` 和经 XML 转义的 `content:encoded` 全文，复用后端 Markdown 清理及附件检查。页内锚点和相对地址补为文章绝对地址，受管附件使用配置的 `PUBLIC_API_BASE_URL`。最近二十篇 PUBLIC 筛选、缓存失效及阅读计数规则保留。

## 参考

实际只读参考了 [FeiTwnd 博客文章页](https://github.com/FeiTwnd/FeiTwnd-Website/blob/master/Frontend-Blog/src/view/Article/index.vue)、[后台编辑页](https://github.com/FeiTwnd/FeiTwnd-Website/blob/master/Frontend-Admin/src/view/Article/ArticleEdit.vue)及其 [MIT 许可证](https://github.com/FeiTwnd/FeiTwnd-Website/blob/master/LICENSE)。借鉴 MdEditor/MdPreview 的使用方式、透明正文背景、网站文字配色、代码容器宽度限制和工具栏层级处理，没有复制整套工程或通过 HTML 开头猜测正文格式。API 按本地安装的 6.3.1 类型和实现核对；[上游文档](https://imzbf.github.io/md-editor-v3/en-US/api/)可能对应更新版本。

## 隔离验证

检查使用真实组件、Service、Markdown 清理器、附件策略和 MyBatis 映射；HTTP 和 Mapper 边界只返回虚构资料，不使用真实账号或业务文章，不启动后端应用或连接数据库。

- 后台 `node --test src/editorPreview.test.mjs` 检查清理和附件 URL 转换。
- `appearance-detail-check.mjs` 检查 HTML 回退行号的实际可见宽度及手机键盘打开主题菜单。
- `appearance-check.mjs` 对 Markdown 与 HTML 回退各检查六主题 × 明暗，核对实际 SQL 关键字颜色、透明正文背景、网站正文色、统一代码背景、复制折叠、目录、手机长内容与刷新恢复；`editor-appearance-check.mjs` 检查后台六主题的 SQL 实际颜色、输入后重渲染和原文/版本保存。
- `output/markdown-reading/editor-theme-check.mjs` 检查六种排版、亮色固定、输入后重渲染、复制/折叠、输入与未保存标记保持、保存原文与版本、刷新恢复。
- `list-theme-check.mjs` 检查列表详情预览及弹窗重开后的偏好保存；`image-lifecycle-check.mjs` 检查图片请求取消、鉴权失败不回退、完整代码空白复制和键盘折叠。
- `PublicMarkdownCheck.java` 检查公开原文、新旧缓存、两次 PUBLIC 复核、失败不读正文或计数，并核对真实 MyBatis SQL；`MarkdownReadingCheck.java` 检查后端清理、语法、附件归属和 RSS。

旧版原生选择器的 `theme-browser-check.mjs`、`html-theme-check.mjs` 是历史验证记录；当前选择器和代码外观以 appearance 系列检查为准。

截图和运行结果保存在忽略目录 `output/playwright/` 与 `output/markdown-reading/`；旧截图不代表最终状态。未验证真实 MySQL、Redis、OSS、跨源部署或第三方 RSS 阅读器，隔离结果不能视作外部联调完成。

最终博客和管理端 `pnpm build`、后端 `mvn -B -ntp -f Backend/pom.xml -DskipTests package` 通过。后端命令为编译打包，不代表业务集成测试；管理端构建保留现有大分包提示，未为本需求调整无关的图表或组件分包。
