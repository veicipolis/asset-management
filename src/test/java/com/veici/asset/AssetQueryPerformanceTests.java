package com.veici.asset;

import com.veici.asset.entity.Asset;
import com.veici.asset.entity.AssetStatus;
import com.veici.asset.repository.AssetRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class AssetQueryPerformanceTests {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    AssetRepository assetRepository;

    @Test
    void measureFilteredPageQuery() {
        assertEquals("asset_management_test",
                jdbc.queryForObject("SELECT DATABASE()", String.class));
        assertEquals("asset_test@localhost",
                jdbc.queryForObject("SELECT CURRENT_USER()", String.class));

        String marker = "PERF-" + UUID.randomUUID()
                .toString().replace("-", "");

        Long categoryId = jdbc.queryForObject(
                "SELECT MIN(id) FROM asset_category", Long.class);
        assertNotNull(categoryId);

        int dataSize = 1000;
        int pageSize = 20;
        int warmupCount = 5;
        int measuredCount = 30;

        try {
            var batch = new ArrayList<Object[]>();

            for (int i = 0; i < dataSize; i++) {
                batch.add(new Object[]{
                        marker + "-" + i,
                        marker,
                        categoryId
                });
            }

            jdbc.batchUpdate("""
                    INSERT INTO asset
                        (asset_code, name, category_id, purchase_date,
                         original_value, location, status, version)
                    VALUES (?, ?, ?, '2026-01-01',
                            1000, '性能测试库房', 'IDLE', 0)
                    """, batch);

            long totalAssets = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM asset", Long.class);

            // 预热不计入测量结果。
            for (int i = 0; i < warmupCount; i++) {
                var page = query(marker, categoryId, i, pageSize);
                checkPage(page, marker, dataSize, pageSize);
            }

            double[] elapsedMs = new double[measuredCount];

            for (int i = 0; i < measuredCount; i++) {
                // 在第 0～49 页之间采样，避免只测第一页。
                int pageNumber = (i * 7) % (dataSize / pageSize);

                long started = System.nanoTime();

                var page = query(
                        marker, categoryId, pageNumber, pageSize);

                elapsedMs[i] =
                        (System.nanoTime() - started) / 1_000_000.0;

                // 正确性检查放在计时结束后。
                checkPage(page, marker, dataSize, pageSize);
            }

            Arrays.sort(elapsedMs);

            double average = Arrays.stream(elapsedMs)
                    .average().orElseThrow();

            // nearest-rank 算法：ceil(0.95 × 样本数)。
            double p95 = elapsedMs[
                    (int) Math.ceil(measuredCount * 0.95) - 1];

            double maximum = elapsedMs[measuredCount - 1];

            System.out.println();
            System.out.println("===== 资产分页查询性能结果 =====");
            System.out.println("测试专用数据量：" + dataSize);
            System.out.println("测试时资产表总行数：" + totalAssets);
            System.out.println("每页记录数：" + pageSize);
            System.out.println("预热次数：" + warmupCount);
            System.out.println("测量次数：" + measuredCount);
            System.out.println("执行方式：单线程串行调用 Repository");
            System.out.printf(
                    Locale.ROOT, "平均耗时：%.3f ms%n", average);
            System.out.printf(
                    Locale.ROOT, "P95 耗时：%.3f ms%n", p95);
            System.out.printf(
                    Locale.ROOT, "最大耗时：%.3f ms%n", maximum);
            System.out.println("==============================");
        } finally {
            // 精确匹配本次随机标记，只清理本次创建的数据。
            jdbc.update(
                    "DELETE FROM asset WHERE name = ?", marker);
        }
    }

    private Page<Asset> query(
            String marker,
            Long categoryId,
            int pageNumber,
            int pageSize) {

        return assetRepository.search(
                null,
                marker,
                categoryId,
                AssetStatus.IDLE,
                PageRequest.of(
                        pageNumber,
                        pageSize,
                        Sort.by(Sort.Direction.ASC, "id")));
    }

    private void checkPage(
            Page<Asset> page,
            String marker,
            int dataSize,
            int pageSize) {

        assertEquals(dataSize, page.getTotalElements());
        assertEquals(pageSize, page.getNumberOfElements());

        for (Asset asset : page.getContent()) {
            assertEquals(marker, asset.getName());
            assertNotNull(asset.getCategory());
            assertNotNull(asset.getCategory().getName());
        }
    }
}