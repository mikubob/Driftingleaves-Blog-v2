package com.xuan;

import com.xuan.controller.admin.ArticleController;
import com.xuan.exception.BusinessException;
import com.xuan.service.IArticleService;
import com.xuan.vo.ArticleAdminDetailVO;
import com.xuan.vo.ArticleProfileVO;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** 自检后台文章详情的响应和参数，不连接数据库或 Redis。 */
public final class ArticleDetailCheck {

    private ArticleDetailCheck() {
    }

    /** 调用真实 Controller，检查完整详情、版本、非法id和业务错误。 */
    public static void main(String[] args) {
        ArticleAdminDetailVO article = new ArticleAdminDetailVO();
        article.setArticleId(Long.MAX_VALUE);
        article.setTitle("私密草稿");
        article.setStatus(0);
        article.setVisibility(2);
        article.setMarkdownContent("# 正文");
        article.setTagIds(List.of(7L));
        String revision = "\"" + "a".repeat(64) + "\"";
        AtomicInteger reads = new AtomicInteger();
        BusinessException missing = new BusinessException(404, "NOT_FOUND", "文章不存在");
        IArticleService service = (IArticleService) Proxy.newProxyInstance(
                IArticleService.class.getClassLoader(), new Class<?>[]{IArticleService.class},
                (proxy, method, arguments) -> {
                    assert method.getName().equals("detail") : "详情不得调用写接口";
                    reads.incrementAndGet();
                    long id = (Long) arguments[0];
                    if (id == 7L) {
                        throw missing;
                    }
                    assert id == Long.MAX_VALUE : "BIGINT id不能丢失精度";
                    return new ArticleProfileVO(article, revision);
                });
        ArticleController controller = new ArticleController(service);
        var response = controller.detail("9223372036854775807");
        assert response.getStatusCode().value() == 200;
        assert revision.equals(response.getHeaders().getFirst("X-Revision"));
        assert response.getBody() != null;
        assert response.getBody().code().equals("OK");
        assert response.getBody().errors().isEmpty();
        assert response.getBody().data() == article : "完整详情不能被列表字段替代";
        assert reads.get() == 1 : "查询详情只读取一次业务详情";

        for (String invalid : List.of("0", "01", "-1", "1.5", "9223372036854775808")) {
            try {
                controller.detail(invalid);
                throw new AssertionError("非法id应被拒绝：" + invalid);
            } catch (BusinessException failure) {
                assert failure.getStatus() == 400;
                assert failure.getCode().equals("VALIDATION_ERROR");
            }
        }
        assert reads.get() == 1 : "非法id不能进入业务查询";
        try {
            controller.detail("7");
            throw new AssertionError("不存在的文章应返回404");
        } catch (BusinessException failure) {
            assert failure == missing : "保留业务错误，不返回成功空详情";
        }
        System.out.println("PASS: article detail response, revision, BIGINT id, invalid ids, business errors");
    }
}
