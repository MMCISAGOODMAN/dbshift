package com.dbshift;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;

import javax.sql.DataSource;

import com.dbshift.config.ConfigLoader;
import com.dbshift.config.DbshiftConfig;
import com.dbshift.core.ChangelogRepository;
import com.dbshift.core.ConnectionFactory;
import com.dbshift.core.MigrationExecutor;
import com.dbshift.core.MigrationLoader;
import com.dbshift.core.MigrationLock;
import com.dbshift.core.MigrationService;
import com.dbshift.core.MigrationValidator;
import com.dbshift.core.PlaceholderResolver;
import com.dbshift.core.ReportGenerator;
import com.dbshift.core.SchemaDiffService;
import com.dbshift.core.SchemaMetadataLoader;
import com.dbshift.core.SqlImpactAnalyzer;
import com.dbshift.model.EnvironmentDiff;
import com.dbshift.model.MigrateResult;
import com.dbshift.model.MigrationPlan;
import com.dbshift.model.MigrationStatus;
import com.dbshift.model.RepairResult;
import com.dbshift.model.RollbackPlan;
import com.dbshift.model.ValidationResult;

/**
 * DbShift framework entry point.
 */
public final class DbshiftEngine {

    private final DbshiftConfig config;
    private final MigrationService migrationService;
    private final ReportGenerator reportGenerator;

    private DbshiftEngine(DbshiftConfig config, DataSource dataSource) {
        this.config = config;
        ConnectionFactory connectionFactory = new ConnectionFactory(dataSource);
        ChangelogRepository changelogRepository = new ChangelogRepository();
        MigrationLoader migrationLoader = new MigrationLoader();
        MigrationValidator migrationValidator = new MigrationValidator(migrationLoader);
        PlaceholderResolver placeholderResolver = new PlaceholderResolver(config.getPlaceholders());
        MigrationExecutor migrationExecutor = new MigrationExecutor(placeholderResolver);
        SqlImpactAnalyzer sqlImpactAnalyzer = new SqlImpactAnalyzer();
        SchemaMetadataLoader schemaMetadataLoader = new SchemaMetadataLoader(connectionFactory);
        SchemaDiffService schemaDiffService = new SchemaDiffService(schemaMetadataLoader);
        MigrationLock migrationLock = new MigrationLock();
        this.migrationService = new MigrationService(
                connectionFactory,
                changelogRepository,
                migrationLoader,
                migrationValidator,
                migrationExecutor,
                sqlImpactAnalyzer,
                schemaDiffService,
                migrationLock,
                placeholderResolver
        );
        this.reportGenerator = new ReportGenerator();
    }

    public static DbshiftEngine fromYaml(Path configFile) throws IOException {
        return fromYaml(configFile, null);
    }

    public static DbshiftEngine fromYaml(Path configFile, DataSource dataSource) throws IOException {
        ConfigLoader loader = new ConfigLoader();
        return new DbshiftEngine(loader.load(configFile), dataSource);
    }

    public static DbshiftEngine fromConfig(DbshiftConfig config) {
        return fromConfig(config, null);
    }

    public static DbshiftEngine fromConfig(DbshiftConfig config, DataSource dataSource) {
        return new DbshiftEngine(config, dataSource);
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

    public void baseline(String environment, String version, String appliedBy) throws SQLException {
        migrationService.baseline(environment, config, version, appliedBy);
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

    public RepairResult repair(String environment) throws IOException, SQLException {
        return migrationService.repair(environment, config);
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

    public void start() {
        DbshiftBootstrap.start(this);
    }
}
