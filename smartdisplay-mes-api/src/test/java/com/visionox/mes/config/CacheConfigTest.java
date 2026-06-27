package com.visionox.mes.config;

import com.visionox.mes.masterdata.service.MasterDataService;
import com.visionox.mes.recipe.dto.RecipeCreateRequest;
import com.visionox.mes.recipe.service.RecipeService;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the read-cache wiring: the cache manager exposes the declared caches, the hot
 * {@code findActiveRecipe} lookup is cacheable, and every recipe write evicts the active-recipe
 * cache so a published/activated/deactivated recipe is never served stale.
 *
 * <p>Reflection + plain-bean assertions, no Spring context (matching the project's test style).
 * The cache annotations are inert in the mocked/unproxied unit tests, so they cannot change
 * behaviour there; this test pins the intent instead.</p>
 */
class CacheConfigTest {

    @Test
    void cacheManagerExposesDeclaredCaches() {
        CacheManager cacheManager = new CacheConfig().cacheManager();
        assertThat(cacheManager.getCacheNames())
                .contains(CacheConfig.ACTIVE_RECIPE, CacheConfig.MASTER_DATA);
    }

    @Test
    void findActiveRecipeIsCacheable() throws Exception {
        Cacheable cacheable = RecipeService.class
                .getMethod("findActiveRecipe", String.class, String.class, String.class)
                .getAnnotation(Cacheable.class);
        assertThat(cacheable).isNotNull();
        assertThat(cacheable.cacheNames()).contains(CacheConfig.ACTIVE_RECIPE);
    }

    @Test
    void everyRecipeWriteEvictsActiveRecipeCache() throws Exception {
        for (String writeMethod : List.of("activateRecipe", "publishRecipe", "deactivateRecipe")) {
            CacheEvict evict = RecipeService.class.getMethod(writeMethod, Long.class).getAnnotation(CacheEvict.class);
            assertThat(evict).as(writeMethod).isNotNull();
            assertThat(evict.cacheNames()).as(writeMethod).contains(CacheConfig.ACTIVE_RECIPE);
            assertThat(evict.allEntries()).as(writeMethod).isTrue();
        }
        CacheEvict createEvict = RecipeService.class
                .getMethod("createRecipe", RecipeCreateRequest.class).getAnnotation(CacheEvict.class);
        assertThat(createEvict).isNotNull();
        assertThat(createEvict.cacheNames()).contains(CacheConfig.ACTIVE_RECIPE);
    }

    @Test
    void masterDataReadsAreCacheable() throws Exception {
        for (Method method : List.of(
                MasterDataService.class.getMethod("getAllSites"),
                MasterDataService.class.getMethod("getAllProcessSteps"),
                MasterDataService.class.getMethod("getAllProductionLines", String.class, String.class),
                MasterDataService.class.getMethod("getAllWorkShifts", String.class, String.class))) {
            Cacheable cacheable = method.getAnnotation(Cacheable.class);
            assertThat(cacheable).as(method.getName()).isNotNull();
            assertThat(cacheable.cacheNames()).as(method.getName()).contains(CacheConfig.MASTER_DATA);
        }
    }
}
