package app.margin.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.margin.data.db.MarginDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Validates every exported schema and every migration path to the current version. When a new
 * schema version is added, add a `migrate_N_to_M` test that inserts representative rows at N,
 * runs the migration and checks the rows survived (data must never be dropped silently).
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val name = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), MarginDatabase::class.java)

    @Test fun currentSchemaOpensAndMatchesExport() {
        helper.createDatabase(name, MarginDatabase.VERSION).apply {
            execSQL("INSERT INTO folders VALUES ('f','F',NULL,NULL,NULL,0,0,0,0)")
            close()
        }
        // Opening through Room validates the schema identity hash against the entities.
        Room.databaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, MarginDatabase::class.java, name)
            .addMigrations(*MarginDatabase.MIGRATIONS).build().apply {
                openHelper.writableDatabase.query("SELECT count(*) FROM folders").use { c -> c.moveToFirst(); check(c.getInt(0) == 1) }
                close()
            }
    }
}
