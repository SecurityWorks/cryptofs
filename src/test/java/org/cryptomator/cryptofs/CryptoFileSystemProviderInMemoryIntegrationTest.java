package org.cryptomator.cryptofs;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.cryptomator.cryptolib.api.Masterkey;
import org.cryptomator.cryptolib.api.MasterkeyLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.cryptomator.cryptofs.CryptoFileSystemProperties.cryptoFileSystemProperties;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CryptoFileSystemProviderInMemoryIntegrationTest {

	private static FileSystem tmpFs;
	private static Path pathToVault;

	@BeforeAll
	public static void beforeAll() {
		tmpFs = Jimfs.newFileSystem(Configuration.unix());
		pathToVault = tmpFs.getPath("/vault");
	}

	@BeforeEach
	public void beforeEach() throws IOException {
		Files.createDirectory(pathToVault);
	}

	@AfterEach
	public void afterEach() throws IOException {
		try (var paths = Files.walk(pathToVault)) {
			var nodes = paths.sorted(Comparator.reverseOrder()).toList();
			for (var node : nodes) {
				Files.delete(node);
			}
		}
	}

	@AfterAll
	public static void afterAll() throws IOException {
		tmpFs.close();
	}

	@Test
	@DisplayName("Replace an existing, shortened file")
	public void testReplaceExistingShortenedFile() throws IOException {
		try (var fs = setupCryptoFs(50, 100, false)) {
			var fiftyCharName2 = "/50char2_50char2_50char2_50char2_50char2_50char.txt"; //since filename encryption increases filename length, 50 cleartext chars are sufficient
			var source = fs.getPath("/source.txt");
			var target = fs.getPath(fiftyCharName2);
			Files.createFile(source);
			Files.createFile(target);

			assertDoesNotThrow(() -> Files.move(source, target, REPLACE_EXISTING));
			assertTrue(Files.notExists(source));
			assertTrue(Files.exists(target));
		}
	}

	/* //TODO https://github.com/cryptomator/cryptofs/issues/176
	@Test
	@DisplayName("Replace an existing, shortened, empty directory")
	public void testReplaceExistingShortenedDirEmpty() throws IOException {
		try (var fs = setupCryptoFs(50, 100, false)) {
			var dirName50Chars = "/target_89_123456789_123456789_123456789_123456789_"; //since filename encryption increases filename length, 50 cleartext chars are sufficient
			var source = fs.getPath("/sourceDir");
			var target = fs.getPath(dirName50Chars);
			Files.createDirectory(source);
			Files.createDirectory(target);

			assertDoesNotThrow(() -> Files.move(source, target, REPLACE_EXISTING));
			assertTrue(Files.notExists(source));
			assertTrue(Files.exists(target));
		}
	}*/

	/* //TODO https://github.com/cryptomator/cryptofs/issues/177
	@Test
	@DisplayName("Replace an existing, shortened symlink")
	public void testReplaceExistingShortenedSymlink() throws IOException {
		try (var fs = setupCryptoFs(50, 100, false)) {
			var source = fs.getPath("/sourceDir");
			var linkedFromSource = fs.getPath("/linkedFromSource.txt");
			var linkedFromSourceContent = "linkedFromSourceContent!";

			var targetName50Chars = "/target_89_123456789_123456789_123456789_123456789_"; //since filename encryption increases filename length, 50 cleartext chars are sufficient
			var target = fs.getPath(targetName50Chars);
			var linkedFromTarget = fs.getPath("/linkedFromTarget.txt");
			var linkedFromTargetContent = "linkedFromTargeContent!";

			Files.createFile(linkedFromSource);
			Files.writeString(linkedFromSource, linkedFromSourceContent, UTF_8);
			Files.createFile(linkedFromTarget);
			Files.writeString(linkedFromTarget, linkedFromTargetContent, UTF_8);

			Files.createSymbolicLink(source, linkedFromSource);
			Files.createSymbolicLink(target, linkedFromTarget);

			assertDoesNotThrow(() -> Files.move(source, target, REPLACE_EXISTING));
			assertTrue(Files.notExists(source));
			assertTrue(Files.exists(target));

			//Assert linked files haven't been changed
			assertTrue(Files.exists(linkedFromSource));
			assertEquals(Files.readString(linkedFromSource, UTF_8), linkedFromSourceContent);
			assertFalse(Files.isSymbolicLink(linkedFromSource));
			assertTrue(Files.isRegularFile(linkedFromSource, LinkOption.NOFOLLOW_LINKS));

			assertTrue(Files.exists(linkedFromTarget));
			assertEquals(Files.readString(linkedFromTarget, UTF_8), linkedFromTargetContent);
			assertFalse(Files.isSymbolicLink(linkedFromTarget));
			assertTrue(Files.isRegularFile(linkedFromTarget, LinkOption.NOFOLLOW_LINKS));

			//Assert link is correct
			assertTrue(Files.isSymbolicLink(target));
			assertTrue(Files.isRegularFile(target /* FOLLOW_LINKS *<remove angle brackets when enabling test>/));
			assertEquals(Files.readSymbolicLink(target), linkedFromSource);
		}
	}*/

	private FileSystem setupCryptoFs(int ciphertextShorteningThreshold, int maxCleartextFilename, boolean readonly) throws IOException {
		byte[] key = new byte[64];
		Arrays.fill(key, (byte) 0x55);
		var keyLoader = Mockito.mock(MasterkeyLoader.class);
		Mockito.when(keyLoader.loadKey(Mockito.any())).thenAnswer(ignored -> new Masterkey(key));
		var properties = CryptoFileSystemProperties.cryptoFileSystemProperties().withKeyLoader(keyLoader).withShorteningThreshold(ciphertextShorteningThreshold).withMaxCleartextNameLength(maxCleartextFilename).withFlags(readonly ? Set.of(CryptoFileSystemProperties.FileSystemFlags.READONLY) : Set.of()).build();
		CryptoFileSystemProvider.initialize(pathToVault, properties, URI.create("test:key"));
		URI fsUri = CryptoFileSystemUri.create(pathToVault);
		return FileSystems.newFileSystem(fsUri, cryptoFileSystemProperties().withKeyLoader(keyLoader).build());
	}

}
