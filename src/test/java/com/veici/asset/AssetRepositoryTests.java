package com.veici.asset;

import com.veici.asset.repository.AssetRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AssetRepositoryTests {

    @Autowired
    private AssetRepository assetRepository;

    @Test
    void shouldReadAssetWithCategory() {
        var asset = assetRepository.findByAssetCode("DEMO-PC-001")
                .orElseThrow(() -> new AssertionError("找不到演示笔记本电脑"));

        assertEquals("演示笔记本电脑", asset.getName());
        assertEquals("电脑设备", asset.getCategory().getName());
        assertEquals(0, new BigDecimal("5000.00")
                .compareTo(asset.getOriginalValue()));
    }

    @Test
    void shouldReadAssetPage() {
        var page = assetRepository.findAll(PageRequest.of(0, 20));

        assertTrue(page.getTotalElements() >= 2);
        assertTrue(assetRepository.existsByAssetCode("DEMO-DESK-001"));

        page.getContent().forEach(asset -> {
            assertNotNull(asset.getCategory().getName());
            System.out.printf("%s | %s | %s%n",
                    asset.getAssetCode(),
                    asset.getName(),
                    asset.getCategory().getName());
        });
    }
}