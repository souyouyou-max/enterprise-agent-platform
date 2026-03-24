package com.sinosig.aip.data.adapter;

/**
 * 外部数据源适配器接口
 * 统一定义数据同步行为，各外部系统实现该接口将数据拉取并写入本地数据仓库。
 *
 * <pre>
 * 数据流：外部平台（Mock）→ DataSourceAdapter.syncData() → 统一数据仓库
 * </pre>
 */
public interface DataSourceAdapter {

    /**
     * 数据源名称（用于日志和监控标识）
     */
    String getSourceName();

    /**
     * 同步数据到本地数据仓库
     * 实现应包含：数据拉取（Mock生成）→ 幂等写入（清除旧数据）→ 批量插入
     */
    void syncData();
}
