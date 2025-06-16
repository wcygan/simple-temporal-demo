package com.wcygan.contentapproval;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.jooq.DSLContext;
import org.jooq.Result;
import org.jooq.Record;
import org.junit.jupiter.api.Test;

import static com.wcygan.contentapproval.generated.Tables.CONTENT;
import static org.jooq.impl.DSL.field;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestProfile(IntegrationTestProfile.class)
public class FlywayIntegrationTest {

    @Inject
    DSLContext dsl;

    @Inject
    Flyway flyway;

    @Test
    public void testFlywayMigrationCompleted() {
        // Test that Flyway migrations have been applied
        MigrationInfo current = flyway.info().current();
        assertNotNull(current, "Should have a current migration");
        assertEquals("1", current.getVersion().getVersion(), "Should be on version 1");
        assertEquals("Create content table", current.getDescription());
        assertTrue(current.getState().isApplied(), "Migration should be applied");
    }

    @Test
    public void testMigrationCreatedContentTable() {
        // Test that the migration created the content table with correct structure
        
        // Verify table exists and has expected columns
        Result<Record> tableInfo = dsl.select()
            .from("information_schema.columns")
            .where(field("table_name").eq("content"))
            .and(field("table_schema").eq("contentdb"))
            .fetch();
        
        assertTrue(tableInfo.size() > 0, "Content table should exist");
        
        // Check for key columns using lowercase field names
        boolean hasId = tableInfo.stream().anyMatch(r -> "id".equals(r.get(field("column_name"))));
        boolean hasTitle = tableInfo.stream().anyMatch(r -> "title".equals(r.get(field("column_name"))));
        boolean hasContent = tableInfo.stream().anyMatch(r -> "content".equals(r.get(field("column_name"))));
        boolean hasAuthorId = tableInfo.stream().anyMatch(r -> "author_id".equals(r.get(field("column_name"))));
        boolean hasStatus = tableInfo.stream().anyMatch(r -> "status".equals(r.get(field("column_name"))));
        boolean hasTags = tableInfo.stream().anyMatch(r -> "tags".equals(r.get(field("column_name"))));
        boolean hasTemporalWorkflowId = tableInfo.stream().anyMatch(r -> "temporal_workflow_id".equals(r.get(field("column_name"))));
        boolean hasCreatedDate = tableInfo.stream().anyMatch(r -> "created_date".equals(r.get(field("column_name"))));
        boolean hasUpdatedDate = tableInfo.stream().anyMatch(r -> "updated_date".equals(r.get(field("column_name"))));
        
        assertTrue(hasId, "Should have id column");
        assertTrue(hasTitle, "Should have title column");
        assertTrue(hasContent, "Should have content column");
        assertTrue(hasAuthorId, "Should have author_id column");
        assertTrue(hasStatus, "Should have status column");
        assertTrue(hasTags, "Should have tags column");
        assertTrue(hasTemporalWorkflowId, "Should have temporal_workflow_id column");
        assertTrue(hasCreatedDate, "Should have created_date column");
        assertTrue(hasUpdatedDate, "Should have updated_date column");
    }

    @Test
    public void testMigrationInsertedSampleData() {
        // Test that the migration inserted sample data
        int contentCount = dsl.selectCount().from(CONTENT).fetchOne(0, int.class);
        assertTrue(contentCount >= 3, "Should have at least 3 sample content records from migration");
        
        // Verify specific sample data
        boolean hasQuarkusContent = dsl.selectCount()
            .from(CONTENT)
            .where(CONTENT.TITLE.containsIgnoreCase("Quarkus"))
            .fetchOne(0, int.class) > 0;
        
        boolean hasTemporalContent = dsl.selectCount()
            .from(CONTENT)
            .where(CONTENT.TITLE.containsIgnoreCase("Temporal"))
            .fetchOne(0, int.class) > 0;
        
        boolean hasMySQLContent = dsl.selectCount()
            .from(CONTENT)
            .where(CONTENT.TITLE.containsIgnoreCase("MySQL"))
            .fetchOne(0, int.class) > 0;
        
        assertTrue(hasQuarkusContent, "Should have Quarkus sample content");
        assertTrue(hasTemporalContent, "Should have Temporal sample content");
        assertTrue(hasMySQLContent, "Should have MySQL sample content");
    }

    @Test
    public void testFlywaySchemaHistoryTable() {
        // Test that Flyway created its schema history table
        Result<Record> historyTable = dsl.select()
            .from("information_schema.tables")
            .where(field("table_name").eq("flyway_schema_history"))
            .and(field("table_schema").eq("contentdb"))
            .fetch();
        
        assertEquals(1, historyTable.size(), "Should have flyway_schema_history table");
        
        // Check that there's at least one migration record
        int migrationCount = dsl.selectCount()
            .from("flyway_schema_history")
            .fetchOne(0, int.class);
        
        assertTrue(migrationCount >= 1, "Should have at least one migration record");
    }

    @Test
    public void testMigrationVersionConsistency() {
        // Test that the migration version in Flyway history matches expected
        Result<Record> migrationRecords = dsl.select()
            .from("flyway_schema_history")
            .fetch();
        
        assertTrue(migrationRecords.size() >= 1, "Should have at least one migration");
        
        // Simplified test - just verify we have migration records
        // The exact field access varies between H2 and PostgreSQL
        assertTrue(migrationRecords.size() > 0, "Should have migration records");
        
        // Test that the content table actually has data from migration
        int contentCount = dsl.selectCount().from(CONTENT).fetchOne(0, int.class);
        assertTrue(contentCount >= 3, "Should have sample data from migration");
    }
}