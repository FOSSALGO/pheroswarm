# PheroSwarm

PheroSwarm is a swarm robotics simulation application using bio-inspired pheromone-based coordination algorithms.

---

## 📖 API Documentation (Javadoc)

Dokumentasi lengkap kelas, fungsi, dan method (Javadoc) dapat diakses secara langsung:
- **Java Doc**: Open [`https://fossalgo.github.io/pheroswarm/javadoc`])*
- **Offline**: Buka file [`docs/index.html`](docs/index.html) atau [`docs/javadoc/index.html`](docs/javadoc/index.html) langsung di browser Anda.

---

## 🚀 Menjalankan Aplikasi (.jar Executable)

Aplikasi ini dapat di-build menjadi file `.jar` runnable mandiri yang berisi seluruh pustaka JavaFX.

### Build Menggunakan Maven:
```powershell
.\mvnw.cmd clean package -DskipTests
```
File JAR runnable akan dihasilkan di:
```
target/pheroswarm-1.0-SNAPSHOT.jar
```
Cukup **double-click** file `.jar` tersebut di Windows Explorer (atau jalankan melalui terminal: `java -jar target/pheroswarm-1.0-SNAPSHOT.jar`).
