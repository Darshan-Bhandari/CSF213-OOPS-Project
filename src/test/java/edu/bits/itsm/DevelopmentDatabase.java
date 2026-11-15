package edu.bits.itsm;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

/** Optional local PostgreSQL process; not packaged in the production application. */
public class DevelopmentDatabase {
  public static void main(String[] args) throws Exception {
    var directory = Path.of(".local", "postgres-data").toAbsolutePath().toFile();
    directory.mkdirs();
    var database =
        EmbeddedPostgres.builder()
            .setPort(54329)
            .setDataDirectory(directory)
            .setCleanDataDirectory(false)
            .setServerConfig("listen_addresses", "127.0.0.1")
            .start();
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  try {
                    database.close();
                  } catch (Exception ignored) {
                  }
                }));
    System.out.println(
        "Local PostgreSQL ready on 127.0.0.1:54329; database=postgres, user=postgres, loopback"
            + " trust authentication.");
    System.out.println(
        "Persistent data: "
            + directory
            + ". Keep this terminal running; Ctrl+C stops the database.");
    new CountDownLatch(1).await();
  }
}
