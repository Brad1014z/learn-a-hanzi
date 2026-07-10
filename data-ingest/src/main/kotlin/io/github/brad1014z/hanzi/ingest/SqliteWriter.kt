package io.github.brad1014z.hanzi.ingest

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Creates the bundled SQLite asset with the schema Room expects (spec 03): the CREATE
 * statements and identity hash are read from the KSP-exported Room schema JSON
 * (app/schemas/…/2.json), so the asset can never drift from what
 * `Room.createFromAsset` validates. User tables are created empty.
 */
class SqliteWriter(schemaJson: File, private val target: File) {

    private val schema: JsonObject =
        json.parseToJsonElement(schemaJson.readText()).jsonObject["database"]!!.jsonObject

    fun <T> write(block: (Connection) -> T): T {
        target.parentFile.mkdirs()
        target.delete()
        File(target.parent, target.name + "-journal").delete()
        return DriverManager.getConnection("jdbc:sqlite:${target.path}").use { conn ->
            conn.createStatement().use { st ->
                for (entity in schema["entities"]!!.jsonArray) {
                    val obj = entity.jsonObject
                    val table = obj["tableName"]!!.jsonPrimitive.content
                    st.executeUpdate(obj["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                    for (index in obj["indices"]?.jsonArray ?: emptyList()) {
                        st.executeUpdate(
                            index.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table),
                        )
                    }
                }
                // room_master_table + identity hash (Room validates this on open).
                for (q in schema["setupQueries"]!!.jsonArray) {
                    st.executeUpdate(q.jsonPrimitive.content)
                }
            }
            conn.autoCommit = false
            val result = block(conn)
            conn.commit()
            result
        }
    }
}
