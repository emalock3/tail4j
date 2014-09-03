package tail4j;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

public class TestFileTest {

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Test
    public void testConstructor() throws Exception {
        TailFile.SourceHolder sourceHolder = new TailFile.SourceHolder(tempDir.newFile().toPath());
        TailFile tailFile = new TailFile(sourceHolder, Charset.defaultCharset(), System.out,
                Charset.defaultCharset(), TailFile.ReadingPos.EMPTY);
        assertThat(tailFile, is(not(nullValue())));
    }

    @Test
    public void testShutdown() throws Exception {
        TailFile.SourceHolder sourceHolder = new TailFile.SourceHolder(tempDir.newFile().toPath());
        TailFile.Thread t = new TailFile.Builder(sourceHolder.getSource(), System.out).build();
        assertThat(t.getState(), is(Thread.State.NEW));
        t.start();
        assertThat(t.getState(), is(not(Thread.State.NEW)));
        assertThat(t.getState(), is(not(Thread.State.TERMINATED)));
        t.get().shutdown();
        t.join();
        assertThat(t.getState(), is(Thread.State.TERMINATED));
    }

    @Test
    public void testRunAndAppendString() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        TailFile.SourceHolder sourceHolder = new TailFile.SourceHolder(tempDir.newFile().toPath());
        TailFile.Thread t = new TailFile.Builder(sourceHolder.getSource(), out).build();
        t.start();
        assertThat(out.toByteArray(), is(new byte[0]));
        String line = "Hello";
        Files.write(sourceHolder.getSource(), line.getBytes(Charset.defaultCharset()), StandardOpenOption.APPEND);
        t.get().handleModifyEvent(sourceHolder.getSource().getFileName());
        Thread.sleep(100L);
        assertThat(new String(out.toByteArray(), Charset.defaultCharset()), is(line));
        t.get().shutdown();
        t.join();
    }

    @Test
    public void testRunAndAppendTwoStrings() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        TailFile.SourceHolder sourceHolder = new TailFile.SourceHolder(tempDir.newFile().toPath());
        TailFile.Thread t = new TailFile.Builder(sourceHolder.getSource(), out).build();
        t.start();
        assertThat(out.toByteArray(), is(new byte[0]));
        String one = "One";
        Files.write(sourceHolder.getSource(), one.getBytes(Charset.defaultCharset()), StandardOpenOption.APPEND);
        t.get().handleModifyEvent(sourceHolder.getSource().getFileName());
        Thread.sleep(100L);
        assertThat(new String(out.toByteArray(), Charset.defaultCharset()), is(one));
        String two = "Two";
        Files.write(sourceHolder.getSource(), two.getBytes(Charset.defaultCharset()), StandardOpenOption.APPEND);
        t.get().handleModifyEvent(sourceHolder.getSource().getFileName());
        Thread.sleep(100L);
        assertThat(new String(out.toByteArray(), Charset.defaultCharset()), is(one + two));
        t.get().shutdown();
        t.join();
    }

    @Test
    public void testRestartTailFileWithPersistOn() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        TailFile.SourceHolder sourceHolder = new TailFile.SourceHolder(tempDir.newFile().toPath());
        TailFile.Builder builder = new TailFile.Builder(sourceHolder.getSource(), out).persist(true);
        try {
            TailFile.Thread t = builder.build();
            t.start();
            String one = "One";
            Files.write(sourceHolder.getSource(), one.getBytes(Charset.defaultCharset()), StandardOpenOption.APPEND);
            t.get().handleModifyEvent(sourceHolder.getSource().getFileName());
            Thread.sleep(100L);
            assertThat(new String(out.toByteArray(), Charset.defaultCharset()), is(one));
            t.get().shutdown();
            t.join();
            out = new ByteArrayOutputStream();
            t = new TailFile.Builder(sourceHolder.getSource(), out).persist(true).build();
            t.start();
            String two = "Two";
            Files.write(sourceHolder.getSource(), two.getBytes(Charset.defaultCharset()), StandardOpenOption.APPEND);
            Thread.sleep(100L);
            assertThat(new String(out.toByteArray(), Charset.defaultCharset()), is(two));
            t.get().shutdown();
            t.join();
        } finally {
            Path posFile = TailFile.Builder.toDefaultPositionFile(builder.holder().getSource());
            Files.deleteIfExists(posFile);
        }
    }

    @Test
    public void testRestartTailFileWithPositionFile() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        TailFile.SourceHolder sourceHolder = new TailFile.SourceHolder(tempDir.newFile().toPath());
        Path posFile = tempDir.newFile().toPath();
        TailFile.Thread t = new TailFile.Builder(sourceHolder.getSource(), out).positionFile(posFile).build();
        t.start();
        String one = "One";
        Files.write(sourceHolder.getSource(), one.getBytes(Charset.defaultCharset()), StandardOpenOption.APPEND);
        t.get().handleModifyEvent(sourceHolder.getSource().getFileName());
        Thread.sleep(100L);
        assertThat(new String(out.toByteArray(), Charset.defaultCharset()), is(one));
        t.get().shutdown();
        t.join();
        out = new ByteArrayOutputStream();
        t = new TailFile.Builder(sourceHolder.getSource(), out).positionFile(posFile).build();
        t.start();
        String two = "Two";
        Files.write(sourceHolder.getSource(), two.getBytes(Charset.defaultCharset()), StandardOpenOption.APPEND);
        Thread.sleep(100L);
        assertThat(new String(out.toByteArray(), Charset.defaultCharset()), is(two));
        t.get().shutdown();
        t.join();
    }

    @Test
    public void testRestartAndReset() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        TailFile.SourceHolder sourceHolder = new TailFile.SourceHolder(tempDir.newFile().toPath());
        Path posFile = tempDir.newFile().toPath();
        TailFile.Thread t = new TailFile.Builder(sourceHolder.getSource(), out).positionFile(posFile).build();
        t.start();
        String one = "One";
        Files.write(sourceHolder.getSource(), one.getBytes(Charset.defaultCharset()), StandardOpenOption.APPEND);
        t.get().handleModifyEvent(sourceHolder.getSource().getFileName());
        Thread.sleep(100L);
        assertThat(new String(out.toByteArray(), Charset.defaultCharset()), is(one));
        t.get().shutdown();
        t.join();
        out = new ByteArrayOutputStream();
        t = new TailFile.Builder(sourceHolder.getSource(), out).positionFile(posFile).reset(true).build();
        t.start();
        String two = "Two";
        Files.write(sourceHolder.getSource(), two.getBytes(Charset.defaultCharset()), StandardOpenOption.APPEND);
        Thread.sleep(100L);
        assertThat(new String(out.toByteArray(), Charset.defaultCharset()), is(one + two));
        t.get().shutdown();
        t.join();
    }

    @Test
    public void testRunWithJISSourceCharset() throws Exception {
        testRunWithSourceCharset(Charset.forName("ISO-2022-JP"), Charset.defaultCharset());
    }

    @Test
    public void testRunWithEUCJPSourceCharset() throws Exception {
        testRunWithSourceCharset(Charset.forName("EUC-JP"), Charset.defaultCharset());
    }

    @Test
    public void testRunWithWin31JSourceCharset() throws Exception {
        testRunWithSourceCharset(Charset.forName("Windows-31J"), Charset.defaultCharset());
    }

    @Test
    public void testRunWithUTF8SourceCharset() throws Exception {
        testRunWithSourceCharset(Charset.forName("UTF-8"), Charset.defaultCharset());
    }

    @Test
    public void testRunWithJISDestCharset() throws Exception {
        testRunWithSourceCharset(Charset.defaultCharset(), Charset.forName("ISO-2022-JP"));
    }

    @Test
    public void testRunWithEUCJPDestCharset() throws Exception {
        testRunWithSourceCharset(Charset.defaultCharset(), Charset.forName("EUC-JP"));
    }

    @Test
    public void testRunWithWin31JDestCharset() throws Exception {
        testRunWithSourceCharset(Charset.defaultCharset(), Charset.forName("Windows-31J"));
    }

    @Test
    public void testRunWithUTF8DestCharset() throws Exception {
        testRunWithSourceCharset(Charset.defaultCharset(), Charset.forName("UTF-8"));
    }

    private void testRunWithSourceCharset(Charset sourceCharset, Charset destCharset) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        TailFile.SourceHolder sourceHolder = new TailFile.SourceHolder(tempDir.newFile().toPath());
        TailFile.Thread t = new TailFile.Builder(sourceHolder.getSource(), out)
                .sourceCharset(sourceCharset).destCharset(destCharset).build();
        t.start();
        String str = String.format("日本語(%s)", sourceCharset.displayName());
        Files.write(sourceHolder.getSource(), str.getBytes(sourceCharset), StandardOpenOption.APPEND);
        t.get().handleModifyEvent(sourceHolder.getSource().getFileName());
        Thread.sleep(100L);
        assertThat(new String(out.toByteArray(), destCharset), is(str));
        t.get().shutdown();
        t.join();
    }
}
