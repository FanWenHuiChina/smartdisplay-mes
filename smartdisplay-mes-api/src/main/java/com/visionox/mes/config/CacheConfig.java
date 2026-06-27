package com.visionox.mes.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 读多写少数据的进程内读缓存。
 *
 * <p>缓存对象：有效配方查找（{@code findActiveRecipe}，每次 Track In 都会读，热点）与
 * 基础主数据（站点/工序/产线/班次，运行期基本不变）。</p>
 *
 * <p>一致性策略：配方的写动作（创建/激活/发布/停用）以 {@code @CacheEvict(allEntries)} 失效
 * 整个有效配方缓存，读多写少下命中率高且失效面可控。试点用 {@link ConcurrentMapCacheManager}
 * （进程内、无 TTL）即可演示缓存与失效；多实例生产部署可平滑替换为 Redis/Caffeine，
 * 并补 TTL 与跨实例失效。</p>
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** 有效配方查找结果缓存（按 产品|工序|设备 维度）。 */
    public static final String ACTIVE_RECIPE = "activeRecipe";

    /** 基础主数据缓存（站点/工序/产线/班次，按查询维度做 key）。 */
    public static final String MASTER_DATA = "masterData";

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager(ACTIVE_RECIPE, MASTER_DATA);
    }
}
