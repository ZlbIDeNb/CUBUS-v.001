import base64
import hashlib
import sqlite3
from datetime import datetime, timezone
from pathlib import Path

from .models import DocumentationCreate, DocumentationContent, DocumentationItem


class DocumentStore:
    def __init__(self) -> None:
        root = Path(__file__).resolve().parent.parent / "server_documents"
        root.mkdir(parents=True, exist_ok=True)
        self.root = root
        self.database = root / "documents.sqlite3"
        with sqlite3.connect(self.database) as connection:
            connection.execute(
                """CREATE TABLE IF NOT EXISTS documents (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    owner TEXT NOT NULL,
                    title TEXT NOT NULL,
                    comment TEXT NOT NULL,
                    filename TEXT NOT NULL,
                    mime_type TEXT NOT NULL,
                    storage_name TEXT NOT NULL,
                    created_at TEXT NOT NULL
                )"""
            )

    def list(self, owner: str) -> list[DocumentationItem]:
        with sqlite3.connect(self.database) as connection:
            rows = connection.execute(
                "SELECT id,title,comment,filename,mime_type,created_at "
                "FROM documents WHERE owner=? ORDER BY id DESC",
                (owner,),
            ).fetchall()
        return [DocumentationItem(
            id=row[0], title=row[1], comment=row[2], filename=row[3],
            mime_type=row[4], created_at=row[5],
        ) for row in rows]

    def add(self, owner: str, command: DocumentationCreate) -> DocumentationItem:
        if command.mime_type not in {"application/pdf", "image/jpeg", "image/jpg"}:
            raise ValueError("Разрешены только PDF и JPEG")
        try:
            content = base64.b64decode(command.content_base64, validate=True)
        except Exception as exc:
            raise ValueError("Файл повреждён") from exc
        if not content or len(content) > 20 * 1024 * 1024:
            raise ValueError("Размер файла должен быть от 1 байта до 20 МБ")
        digest = hashlib.sha256(content).hexdigest()
        suffix = ".pdf" if command.mime_type == "application/pdf" else ".jpg"
        storage_name = digest + suffix
        (self.root / storage_name).write_bytes(content)
        created_at = datetime.now(timezone.utc).isoformat()
        with sqlite3.connect(self.database) as connection:
            cursor = connection.execute(
                "INSERT INTO documents(owner,title,comment,filename,mime_type,storage_name,created_at) "
                "VALUES(?,?,?,?,?,?,?)",
                (owner, command.title.strip(), command.comment.strip(), command.filename,
                 command.mime_type, storage_name, created_at),
            )
            document_id = int(cursor.lastrowid)
        return DocumentationItem(
            id=document_id, title=command.title.strip(), comment=command.comment.strip(),
            filename=command.filename, mime_type=command.mime_type, created_at=created_at,
        )

    def content(self, owner: str, document_id: int) -> DocumentationContent:
        with sqlite3.connect(self.database) as connection:
            row = connection.execute(
                "SELECT filename,mime_type,storage_name FROM documents WHERE owner=? AND id=?",
                (owner, document_id),
            ).fetchone()
        if row is None:
            raise KeyError(document_id)
        path = self.root / row[2]
        return DocumentationContent(
            filename=row[0], mime_type=row[1],
            content_base64=base64.b64encode(path.read_bytes()).decode("ascii"),
        )

    def delete(self, owner: str, document_id: int) -> None:
        with sqlite3.connect(self.database) as connection:
            row = connection.execute(
                "SELECT storage_name FROM documents WHERE owner=? AND id=?",
                (owner, document_id),
            ).fetchone()
            if row is None:
                raise KeyError(document_id)
            connection.execute("DELETE FROM documents WHERE owner=? AND id=?", (owner, document_id))
            still_used = connection.execute(
                "SELECT 1 FROM documents WHERE storage_name=? LIMIT 1", (row[0],)
            ).fetchone()
        if still_used is None:
            (self.root / row[0]).unlink(missing_ok=True)


document_store = DocumentStore()
