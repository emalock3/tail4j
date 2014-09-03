package tail4j;

import org.kohsuke.args4j.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Tail {

    @Option(name = "-h", aliases = "help", usage = "show this message")
    private boolean help;
    @Option(name = "-e", aliases = "encode", usage = "source file encoding (default = Platform's default charset)")
    private String encode;
    @Option(name = "-r", aliases = "reset", usage = "reset previous reading position (default = false)")
    private boolean reset;
    @Option(name = "-p", aliases = "persistence", usage = "persist last reading position (default = false)")
    private boolean persist;
    @Option(name = "-P", aliases = "pos-file", usage = "persist last reading position to POS-FILE (default = /<java.io.tmpdir>/<TEMPORARY-FILE>)")
    private File positionFile;
    @Argument
    private List<String> arguments = new ArrayList<>();

    public static void main(String ... args) throws IOException, InterruptedException {
        new Tail().doMain(args);
    }

    private void printUsage(CmdLineParser parser) {
        System.err.println("tail4j [options...] watch-file-path");
        parser.printUsage(System.err);
        System.err.println("  Example: tail4j" + parser.printExample(OptionHandlerFilter.ALL));
    }

    public void doMain(String... args) throws InterruptedException {
        if (!init(args)) {
            return;
        }
        TailFile.Builder builder = createBuilder();
        WatchDir wd = new WatchDir(builder);
        wd.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                e.printStackTrace();
                System.exit(-1);
            }
        });
        wd.start();
        wd.join();
    }

    private boolean init(String... args) {
        CmdLineParser parser = new CmdLineParser(this);
        parser.setUsageWidth(80);
        try {
            parser.parseArgument(args);
            if (help) {
                printUsage(parser);
                return false;
            }
            if (arguments.isEmpty()) {
                throw new CmdLineException(parser, "No argument is given");
            }
        } catch (CmdLineException e) {
            System.err.println(e.getMessage());
            printUsage(parser);
            System.exit(-1);
            return false;
        }
        return true;
    }

    private TailFile.Builder createBuilder() {
        TailFile.Builder builder = new TailFile.Builder(Paths.get(arguments.get(0)), System.out);
        if (reset) {
            builder = builder.reset(true);
        }
        if (encode != null) {
            builder = builder.sourceCharset(Charset.forName(encode));
        }
        if (persist) {
            builder = builder.persist(true);
        }
        if (positionFile != null) {
            builder = builder.positionFile(positionFile.toPath());
        }
        return builder;
    }
}
