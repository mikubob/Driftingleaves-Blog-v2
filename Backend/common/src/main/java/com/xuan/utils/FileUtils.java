package com.xuan.utils;

/** 文件类型工具，根据扩展名确定存储目录，上传允许的类型另行校验。 */
public final class FileUtils {

    private FileUtils() {
    }

    /** 根据扩展名查询文件分类，参数为不带点的小写后缀，未知类型返回 other。 */
    public static String getFileCategory(String extension) {
        return switch (extension) {
            case "jpg", "png", "gif", "bmp", "webp", "jpeg", "svg", "ico", "tiff" -> "image";
            case "mp4", "avi", "mov", "mkv", "wmv", "flv", "webm", "m4v", "3gp" -> "video";
            case "mp3", "wav", "wma", "ogg", "aac", "flac", "m4a", "ape", "mid", "midi" -> "audio";
            case "lrc", "lrcx", "krc", "qrc", "trc", "ksc" -> "lyric";
            case "txt", "json", "md", "rtf" -> "text";
            case "pdf" -> "pdf";
            case "doc", "docx", "dot", "dotx" -> "word";
            case "xls", "xlsx", "xlt", "xltx" -> "excel";
            case "zip", "rar", "7z", "tar", "gz", "bz2" -> "archive";
            case "ttf", "otf", "woff", "woff2", "eot" -> "font";
            default -> "other";
        };
    }

    /** 根据扩展名获取 MIME 类型，上传、保存资料和读取文件使用相同规则。 */
    public static String mimeType(String extension) {
        return switch (extension) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "mp3" -> "audio/mpeg";
            case "wav" -> "audio/wav";
            case "ogg" -> "audio/ogg";
            case "flac" -> "audio/flac";
            case "lrc", "txt" -> "text/plain";
            case "json" -> "application/json";
            default -> null;
        };
    }

    /** 根据文件类型获取大小上限，单位为字节，图片、歌词和音频分别限制。 */
    public static int maxBytes(String extension) {
        String mime = mimeType(extension);
        if (mime == null) {
            return 0;
        }
        if (mime.startsWith("image/")) {
            return 10 * 1024 * 1024;
        }
        return mime.startsWith("audio/") ? 60 * 1024 * 1024 : 1024 * 1024;
    }

    /** 检查文件存储路径，本地和 OSS 都只允许分类正确的随机文件名，不接受客户端路径。 */
    public static boolean validObjectKey(String key) {
        if (key == null || !key.matches("[a-z]+/[A-Za-z0-9_-]+\\.[a-z0-9]+")) {
            return false;
        }
        String extension = key.substring(key.lastIndexOf('.') + 1);
        return mimeType(extension) != null && key.startsWith(getFileCategory(extension) + "/");
    }
}
