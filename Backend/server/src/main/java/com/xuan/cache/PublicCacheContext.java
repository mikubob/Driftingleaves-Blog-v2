package com.xuan.cache;

import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** 在同一线程内保存缓存调用层次，嵌套查询共用代次，最外层退出时释放。 */
public final class PublicCacheContext {

    private static final ThreadLocal<Deque<Frame>> CURRENT = ThreadLocal.withInitial(ArrayDeque::new);

    private PublicCacheContext() {
    }

    /** 每层保留返回类型和失效范围，代次由同一条调用链共用。 */
    public record Frame(Type type, Set<String> evictions, Map<String, String> generations, boolean bypass) {
    }

    /** 在事务创建读取快照之前入栈；已有外层事务时沿用原来的旁路规则。 */
    public static void open(Type type, Set<String> evictions) {
        Frame parent = current();
        Map<String, String> generations = parent == null ? new HashMap<>() : parent.generations();
        boolean bypass = parent == null
                ? TransactionSynchronizationManager.isActualTransactionActive() : parent.bypass();
        CURRENT.get().push(new Frame(type, evictions, generations, bypass));
    }

    /** 无论调用成功还是抛错都要出栈，不能把本次查询的类型留给后续请求。 */
    public static void close() {
        CURRENT.get().pop();
        if (CURRENT.get().isEmpty()) {
            CURRENT.remove();
        }
    }

    /** 存储与序列化只使用当前层的查询类型、代次及事务状态。 */
    public static Frame current() {
        return CURRENT.get().peek();
    }

    /** 写入前汇总当前调用链的失效范围，内部代理调用不能丢失外层声明。 */
    public static Set<String> evictions() {
        Set<String> domains = new LinkedHashSet<>();
        for (Frame frame : CURRENT.get()) {
            domains.addAll(frame.evictions());
        }
        return domains;
    }
}
