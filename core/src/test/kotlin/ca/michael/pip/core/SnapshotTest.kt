package ca.michael.pip.core

import ca.michael.pip.core.snapshot.Role
import ca.michael.pip.core.snapshot.SnapshotFormatter
import ca.michael.pip.core.snapshot.SnapshotNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SnapshotTest {
    private val tree = SnapshotNode(Role.OTHER, null, children = listOf(
        SnapshotNode(Role.TEXT, "Connexion"),
        SnapshotNode(Role.FIELD, "michael@example.org", focused = true),
        SnapshotNode(Role.FIELD, "hunter2-value", password = true),
        SnapshotNode(Role.TEXT, "caché", visible = false),
        SnapshotNode(Role.LIST, "Options", children = listOf(
            SnapshotNode(Role.TOGGLE, "Rester connecté", checked = true),
        )),
        SnapshotNode(Role.BUTTON, "Entrer", disabled = true),
    ))

    @Test fun writesTheDocumentedFormat() {
        val out = SnapshotFormatter.format("org.example.app", "2026-10-29T12:00:00Z", tree)
        assertEquals(
            """
            [pip-snapshot v1] app=org.example.app time=2026-10-29T12:00:00Z nodes=5 truncated=no
            text "Connexion"
            field "michael@example.org" [focused]
            list "Options"
              toggle "Rester connecté" [checked]
            button "Entrer" [disabled]
            """.trimIndent(),
            out,
        )
    }

    /** T-5: password fields are excluded entirely. */
    @Test fun passwordFieldsNeverAppear() {
        val out = SnapshotFormatter.format("p", "t", tree)
        assertFalse("hunter2" in out)
        assertFalse("caché" in out)
    }

    @Test fun capsAreMarkedTruncated() {
        val big = SnapshotNode(Role.LIST, null, children = List(500) { SnapshotNode(Role.ITEM, "ligne $it") })
        val out = SnapshotFormatter.format("p", "t", big)
        assertTrue(out.startsWith("[pip-snapshot v1] app=p time=t nodes=200 truncated=yes"))
    }
}
