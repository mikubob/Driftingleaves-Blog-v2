package com.xuan.cache;

import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** 在线程内记录每层缓存调用，嵌套查询共用缓存版本，最外层调用结束时清理。 */
public final class PublicCacheContext {

    private static final ThreadLocal<Deque<Frame>> CURRENT = ThreadLocal.withInitial(ArrayDeque::new);

    private PublicCacheContext() {
    }

    /** 记录每层的返回类型和要清理的缓存，同一条调用链共用缓存版本。 */
    public record Frame(Type type, Set<String> evictions, Map<String, String> generations, boolean bypass) {
    }

    /** 在事务第一次查询前记录本层缓存信息；已有外层事务时，继续按原规则跳过缓存。 */
    public static void open(Type type, Set<String> evictions) {
        Frame parent = current();
        Map<String, String> generations = parent == null ? new HashMap<>() : parent.generations();
        boolean bypass = parent == null
                ? TransactionSynchronizationManager.isActualTransactionActive() : parent.bypass();
        CURRENT.get().push(new Frame(type, evictions, generations, bypass));
    }

    /** 清理当前层缓存信息，成功或异常时都要执行，避免影响后续请求。 */
    public static void close() {
        CURRENT.get().pop();
        if (CURRENT.get().isEmpty()) {
            CURRENT.remove();
        }
    }

    /** 获取当前层的查询类型、缓存版本和事务状态，供缓存读写和序列化使用。 */
    public static Frame current() {
        return CURRENT.get().peek();
    }

    /** 汇总本次调用要清理的缓存，保留外层方法声明的范围。 */
    public static Set<String> evictions() {
        Set<String> domains = new LinkedHashSet<>();
        for (Frame frame : CURRENT.get()) {
            domains.addAll(frame.evictions());
        }
        return domains;
    }
}
