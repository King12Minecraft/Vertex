import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;

/**
 * CodeEditorWindow
 * ----------------
 * The "write code in a screen itself" half of the Roblox-style custom
 * games feature - a plain text editor for a single Java source file,
 * with Compile & Run (try it out locally, no server round-trip) and
 * Publish to Server (compiles fresh, packages a jar, uploads it - same
 * request UploadCustomGameDialog's "Upload Project" path uses, see
 * CustomGameUploader).
 *
 * Deliberately NOT themed (no ThemeManager/RoundedPanel/UITheme) - a
 * code editor reads better in a plain, high-contrast, fixed-width look
 * than in Vertex's own glassy/teal theme, and this window was
 * specifically asked not to need to follow it. Standard Swing
 * components, default look and feel.
 */
public class CodeEditorWindow extends JFrame
{
    private static final String STARTER_TEMPLATE =
        "import javax.swing.*;\n" +
        "import java.awt.*;\n" +
        "\n" +
        "// Your entry class must be public, extend JFrame, and have a public\n" +
        "// no-argument constructor - Vertex creates one and shows it, exactly\n" +
        "// like every built-in game already works.\n" +
        "public class MyGame extends JFrame\n" +
        "{\n" +
        "    public MyGame()\n" +
        "    {\n" +
        "        setTitle(\"My Game\");\n" +
        "        setSize(600, 400);\n" +
        "        setLocationRelativeTo(null);\n" +
        "        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);\n" +
        "\n" +
        "        JLabel label = new JLabel(\"Hello, Vertex!\", SwingConstants.CENTER);\n" +
        "        label.setFont(new Font(\"SansSerif\", Font.BOLD, 24));\n" +
        "        add(label);\n" +
        "    }\n" +
        "}\n";

    private final JTextField nameField;
    private final JTextArea codeArea;
    private final JTextArea outputArea;
    private final JButton runButton;
    private final JButton publishButton;

    private final Runnable onPublished;

    public CodeEditorWindow(Runnable onPublished)
    {
        super("Vertex - Code Editor");
        this.onPublished = onPublished;

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(900, 700);
        setLocationByPlatform(true);

        JPanel root = new JPanel(new BorderLayout());
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        setContentPane(root);

        JPanel nameRow = new JPanel(new BorderLayout(8, 0));
        nameRow.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        nameRow.add(new JLabel("Game name:"), BorderLayout.WEST);
        nameField = new JTextField("My Game");
        nameRow.add(nameField, BorderLayout.CENTER);
        root.add(nameRow, BorderLayout.NORTH);

        codeArea = new JTextArea(STARTER_TEMPLATE);
        codeArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        codeArea.setTabSize(4);
        codeArea.setLineWrap(false);
        JScrollPane codeScroll = new JScrollPane(codeArea);

        outputArea = new JTextArea(8, 0);
        outputArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        outputArea.setEditable(false);
        outputArea.setBackground(new Color(245, 245, 245));
        JScrollPane outputScroll = new JScrollPane(outputArea);
        outputScroll.setPreferredSize(new Dimension(0, 150));

        javax.swing.JSplitPane split = new javax.swing.JSplitPane(
            javax.swing.JSplitPane.VERTICAL_SPLIT, codeScroll, outputScroll);
        split.setResizeWeight(0.75);
        root.add(split, BorderLayout.CENTER);

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        runButton = new JButton("Compile & Run");
        runButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { compileAndRun(); }
        });
        buttonRow.add(runButton);

        publishButton = new JButton("Publish to Server");
        publishButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { compileAndPublish(); }
        });
        buttonRow.add(publishButton);
        root.add(buttonRow, BorderLayout.SOUTH);

        if (!InAppCompiler.isAvailable())
        {
            runButton.setEnabled(false);
            publishButton.setEnabled(false);
            outputArea.setText(
                "Live compiling isn't available in this build (no JDK bundled with the app you're running).\n"
                + "Write and compile your game elsewhere instead (e.g. BlueJ), export it as a .jar, and use\n"
                + "\"Upload Project\" on the Custom Games page instead of this editor.");
        }
    }

    private void setButtonsEnabled(boolean enabled)
    {
        runButton.setEnabled(enabled);
        publishButton.setEnabled(enabled);
    }

    private void compileAndRun()
    {
        final String source = codeArea.getText();
        setButtonsEnabled(false);
        outputArea.setText("Compiling...\n");

        Thread worker = new Thread(new Runnable()
        {
            public void run()
            {
                final InAppCompiler.Result result = InAppCompiler.compile(source);

                SwingUtilities.invokeLater(new Runnable()
                {
                    public void run()
                    {
                        outputArea.setText(result.log);
                        setButtonsEnabled(InAppCompiler.isAvailable());

                        if (!result.success)
                        {
                            return;
                        }

                        try
                        {
                            CustomGameLoader.launchFromClassesDir(result.outputDir, result.className);
                        }
                        catch (Exception ex)
                        {
                            ex.printStackTrace();
                            outputArea.setText(outputArea.getText() + "\n\nCould not launch: " + ex);
                        }
                    }
                });
            }
        });
        worker.start();
    }

    private void compileAndPublish()
    {
        final String source = codeArea.getText();
        final String gameName = nameField.getText().trim();

        if (gameName.isEmpty())
        {
            outputArea.setText("Give your game a name first (the field at the top).");
            return;
        }

        setButtonsEnabled(false);
        outputArea.setText("Compiling...\n");

        Thread worker = new Thread(new Runnable()
        {
            public void run()
            {
                final InAppCompiler.Result result = InAppCompiler.compile(source);

                if (!result.success)
                {
                    SwingUtilities.invokeLater(new Runnable()
                    {
                        public void run()
                        {
                            outputArea.setText(result.log);
                            setButtonsEnabled(InAppCompiler.isAvailable());
                        }
                    });
                    return;
                }

                byte[] jarBytes;
                try
                {
                    jarBytes = InAppCompiler.packageJar(result.outputDir);
                }
                catch (IOException e)
                {
                    showError("Compiled, but could not package the jar: " + e.getMessage());
                    return;
                }

                final String errorMessage = CustomGameUploader.upload(gameName, result.className, jarBytes);

                SwingUtilities.invokeLater(new Runnable()
                {
                    public void run()
                    {
                        setButtonsEnabled(InAppCompiler.isAvailable());
                        if (errorMessage != null)
                        {
                            outputArea.setText("Compiled successfully, but publishing failed:\n" + errorMessage);
                            return;
                        }
                        outputArea.setText("Published \"" + gameName + "\" - it's now on the Custom Games page for everyone.");
                        if (onPublished != null)
                        {
                            onPublished.run();
                        }
                    }
                });
            }
        });
        worker.start();
    }

    private void showError(final String message)
    {
        SwingUtilities.invokeLater(new Runnable()
        {
            public void run()
            {
                outputArea.setText(message);
                setButtonsEnabled(InAppCompiler.isAvailable());
            }
        });
    }
}
