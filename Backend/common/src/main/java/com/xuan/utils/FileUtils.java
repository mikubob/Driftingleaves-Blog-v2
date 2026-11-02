package com.xuan.utils;

/** 按扩展名确定存储目录；目录分类不代表上传接口允许该类型。 */
public final class FileUtils {

    private FileUtils() {
    }

    /** 接收不带点的小写扩展名，未列出的文件统一归入 other。 */
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

    /** 上传、存储元数据和读取共用同一份类型约定；分类目录不会放开其他类型。 */
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

    /** 图片沿用原上限，歌词只收小文本，音频允许无损曲目。 */
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

    /** 本地和 OSS 只接收分类正确的随机键，不接受路径整理或客户端文件名。 */
    public static boolean validObjectKey(String key) {
        if (key == null || !key.matches("[a-z]+/[A-Za-z0-9_-]+\\.[a-z0-9]+")) {
            return false;
        }
        String extension = key.substring(key.lastIndexOf('.') + 1);
        return mimeType(extension) != null && key.startsWith(getFileCategory(extension) + "/");
    }
}
