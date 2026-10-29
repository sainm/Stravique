package dev.stravique.application;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PaginationOrderTest {

    @Test
    void everyPagedQueryOrdersByAUniqueKeyLast() {
        assertTotalOrder("files", InventoryQueryService.FILE_ORDER);
        assertTotalOrder("findings", InventoryQueryService.FINDING_ORDER);
        assertTotalOrder("relations", InventoryQueryService.GRAPH_ORDER);
        assertTotalOrder("migration items", MigrationPlanService.ITEM_ORDER);
    }

    private void assertTotalOrder(String name, Sort sort) {
        List<String> properties = sort.stream().map(Sort.Order::getProperty).toList();
        assertThat(properties).describedAs("%s order", name).isNotEmpty();
        assertThat(properties).describedAs("%s order must not repeat a column", name)
                .doesNotHaveDuplicates();
        assertThat(properties.getLast())
                .describedAs("%s order must end at the primary key, or paging over equal "
                        + "sort keys can repeat one row and skip another", name)
                .isEqualTo("id");
    }
}
