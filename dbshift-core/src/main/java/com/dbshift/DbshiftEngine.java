package com.dbshift;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;

import com.dbshift.config.ConfigLoader;
import com.dbshift.config.DbshiftConfig;
import com.dbshift.core.ChangelogRepository;
import com.dbshift.core.ConnectionFactory;
import com.dbshift.core.MigrationExecutor;
import com.dbshift.core.MigrationLoader;
import com.dbshift.core.MigrationService;
import com.dbshift.core.MigrationValidator;
import com.dbshift.core.ReportGenerator;
import com.dbshift.core.SchemaDiffService;
import com.dbshift.core.SchemaMetadataLoader;
import com.dbshift.core.SqlImpactAnalyzer;
import com.dbshift.model.EnvironmentDiff;
import com.dbshift.model.MigrateResult;
import com.dbshift.model.MigrationPlan;
import com.dbshift.model.MigrationStatus;
import com.dbshift.model.RollbackPlan;
import com.dbshift.model.ValidationResult;

/**
 * DbShift framework entry point. Wire this into your app, CI pipeline, or custom tooling.
 *
 * <pre>{@code
 * // 自动迁移（yaml 中 autoMigrate.enabled=true）
 * DbshiftBootstrap.start(Path.of("dbshift.yaml"));
 *
 * // 或手动控制
 * DbshiftEngine engine = DbshiftEngine.fromYaml(Path.of("dbshift.yaml"));
 * engine.start();  // 读取 yaml 自动迁移配置
 * engine.migrate("dev", null, false, true, "my-app");
 * }</pre>
 */
public final class DbshiftEngine {

    private final DbshiftConfig config;
    private final MigrationService migrationService;
    private final ReportGenerator reportGenerator;

    private DbshiftEngine(DbshiftConfig config) {
        this.config = config;
        ConnectionFactory connectionFactory = new ConnectionFactory();
        ChangelogRepository changelogRepository = new ChangelogRepository();
        MigrationLoader migrationLoader = new MigrationLoader();
        MigrationValidator migrationValidator = new MigrationValidator(migrationLoader);
        MigrationExecutor migrationExecutor = new MigrationExecutor();
        SqlImpactAnalyzer sqlImpactAnalyzer = new SqlImpactAnalyzer();
        SchemaMetadataLoader schemaMetadataLoader = new SchemaMetadataLoader(connectionFactory);
        SchemaDiffService schemaDiffService = new SchemaDiffService(schemaMetadataLoader);
        this.migrationService = new MigrationService(
                connectionFactory,
                changelogRepository,
                migrationLoader,
                migrationValidator,
                migrationExecutor,
                sqlImpactAnalyzer,
                schemaDiffService
        );
        this.reportGenerator = new ReportGenerator();
    }

    public static DbshiftEngine fromYaml(Path configFile) throws IOException {
        ConfigLoader loader = new ConfigLoader();
        return new DbshiftEngine(loader.load(configFile));
    }

    public static DbshiftEngine fromConfig(DbshiftConfig config) {
        return new DbshiftEngine(config);
    }

    public DbshiftConfig config() {
        return config;
    }

    public ReportGenerator reports() {
        return reportGenerator;
    }

    public MigrationService migrations() {
        return migrationService;
    }

    public void init(String environment) throws SQLException {
        migrationService.init(environment, config);
    }

    public MigrationStatus status(String environment) throws IOException, SQLException {
        return migrationService.status(environment, config);
    }

    public MigrationPlan plan(String environment, String targetVersion, boolean dryRun)
            throws IOException, SQLException {
        return migrationService.plan(environment, config, targetVersion, dryRun);
    }

    public MigrateResult migrate(String environment, String targetVersion,
                                 boolean requireConfirm, boolean confirmed, String appliedBy)
            throws IOException, SQLException {
        return migrationService.migrate(environment, config, targetVersion, requireConfirm, confirmed, appliedBy);
    }

    public RollbackPlan rollbackPlan(String environment, int steps, boolean dryRun)
            throws IOException, SQLException {
        return migrationService.rollbackPlan(environment, config, steps, dryRun);
    }

    public MigrateResult rollback(String environment, int steps, boolean dryRun,
                                  boolean confirmed, String appliedBy) throws IOException, SQLException {
        return migrationService.rollback(environment, config, steps, dryRun, confirmed, appliedBy);
    }

    public ValidationResult validate() throws IOException {
        return migrationService.validate(config);
    }

    public EnvironmentDiff diffEnvironments(boolean includeSchemaDiff, String schemaFrom, String schemaTo)
            throws IOException, SQLException {
        return migrationService.diffEnvironments(config, includeSchemaDiff, schemaFrom, schemaTo);
    }

    /**
     * Run auto-migration if enabled in {@code dbshift.yaml} → {@code autoMigrate.enabled=true}.
     */
    public void start() {
        DbshiftBootstrap.start(this);
    }
}
