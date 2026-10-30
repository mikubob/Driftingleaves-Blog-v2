# 离线 IP 地域数据库

`ip2region_v4.xdb`、`ip2region_v6.xdb` 是按用户要求从只读老项目
`D:/CodingFiles/Blog/DriftingLeaves/DL-common/src/main/resources/` 引入的公共地域库快照，文件未修改。
上游为 [lionsoul2014/ip2region](https://github.com/lionsoul2014/ip2region)，本项目客户端版本为 `org.lionsoul:ip2region:3.3.7`。
上游许可保留在本目录 LICENSE.md 中，随资源一同打包。

| 资源 | 字节数 | SHA-256 |
| --- | ---: | --- |
| ip2region_v4.xdb | 10641896 | 59d4ce4ffd9dfcab94ff08dd0fa842e3cc7f19684f16a12396e80a0226a5d3a7 |
| ip2region_v6.xdb | 36086712 | 5859527ac7a44b9207d08e303b3d10634d01d0a5ced85dc3f17928c40829877c |

3.x 配套数据字段为 `国家|省份|城市|ISP|国家代码`，不要按旧 2.x 字段位置解析。
这是 IP 归属地域估计，不是设备精确位置或街道地址，结果随网络出口及数据库快照变化。
更新时从上游取得匹配的双协议 xdb，替换源资源、更新本说明中的指纹后重新构建并重启，
运行真实地域查询测试；当前不提供在线下载、热更新或自动覆盖数据库。
