package com.xuan.handle;

/** 将请求字段转换成页面上的中文名称，不回显用户输入。 */
final class RequestMessages {

    private RequestMessages() {
    }

    /** 解析列表和嵌套字段的名称，未知字段使用普通填写提示。 */
    static String name(String field) {
        if (field == null) {
            return "填写内容";
        }
        String name = field.substring(field.lastIndexOf('.') + 1).replaceAll("\\[[^]]*]", "");
        return switch (name) {
            case "title" -> "标题";
            case "slug" -> "URL 标识";
            case "summary" -> "摘要";
            case "categoryId", "categoryName" -> "分类";
            case "tagIds", "tagName" -> "标签";
            case "coverUrl" -> "封面地址";
            case "articleType" -> "文章类型";
            case "originalUrl" -> "原文地址";
            case "visibility" -> "可见性";
            case "isTop" -> "置顶设置";
            case "allowComment" -> "评论设置";
            case "markdownContent", "content" -> "正文";
            case "username" -> "用户名";
            case "nickname" -> "昵称";
            case "email" -> "邮箱";
            case "emailCode" -> "邮箱验证码";
            case "password", "newPassword" -> "密码";
            case "currentPassword" -> "当前密码";
            case "avatar" -> "头像地址";
            case "intro" -> "个人简介";
            case "roleCode" -> "角色编码";
            case "roleName" -> "角色名称";
            case "roleIds", "roleId" -> "角色";
            case "permissionCode" -> "权限编码";
            case "permissionName" -> "权限名称";
            case "permissionIds", "permissionId" -> "权限";
            case "module" -> "所属模块";
            case "menuCode" -> "菜单编码";
            case "menuName" -> "菜单名称";
            case "menuIds" -> "菜单";
            case "menuType" -> "菜单类型";
            case "routeName" -> "路由名称";
            case "path" -> "路由路径";
            case "component" -> "页面组件";
            case "icon" -> "图标";
            case "parentId" -> "上级记录";
            case "keepAlive" -> "页面缓存设置";
            case "isVisible" -> "菜单显示设置";
            case "dataScope" -> "数据范围";
            case "description" -> "描述";
            case "status" -> "状态";
            case "sortOrder" -> "排序";
            case "page" -> "页码";
            case "pageSize" -> "每页条数";
            case "keyword" -> "搜索关键词";
            case "limit" -> "查询数量";
            case "begin" -> "开始时间";
            case "end" -> "结束时间";
            case "month" -> "月份";
            case "id", "ids", "items" -> "所选记录";
            case "revision", "If-Revision" -> "编辑版本";
            case "file" -> "上传文件";
            case "linkName" -> "友链名称";
            case "url" -> "链接地址";
            case "name" -> "名称";
            case "userId" -> "用户";
            case "authorId" -> "作者";
            case "uploaderId" -> "上传者";
            case "articleId" -> "文章";
            case "isSecret" -> "悄悄话设置";
            case "notifyReply" -> "回复通知设置";
            case "contentType" -> "正文格式";
            case "configValue" -> "配置内容";
            case "isPublic" -> "公开设置";
            case "experienceType" -> "经历类型";
            case "subtitle" -> "副标题";
            case "logoUrl" -> "标志图片地址";
            case "startDate" -> "开始日期";
            case "endDate" -> "结束日期";
            case "artist" -> "歌手";
            case "audioUrl" -> "音频地址";
            case "lyricUrl" -> "歌词地址";
            case "lyricType" -> "歌词格式";
            case "durationSeconds" -> "时长（秒）";
            case "blockedUntil" -> "封禁结束时间";
            case "isBlocked" -> "封禁状态";
            case "country" -> "国家";
            case "province" -> "省份";
            case "city" -> "城市";
            case "siteCode" -> "所属站点";
            case "pagePath" -> "访问路径";
            case "pageTitle" -> "页面标题";
            case "referer" -> "来源地址";
            case "fingerprint" -> "访客标识";
            case "visitorId" -> "访客";
            case "mimeType" -> "文件类型";
            case "operationType" -> "操作类型";
            case "operationTarget" -> "操作对象";
            case "result" -> "操作结果";
            case "targetId" -> "目标记录";
            case "trash" -> "回收站筛选";
            case "source" -> "第三方登录平台";
            case "ticket" -> "验证凭证";
            default -> "填写内容";
        };
    }
}
