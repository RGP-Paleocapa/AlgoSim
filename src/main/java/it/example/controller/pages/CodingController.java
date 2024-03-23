package it.example.controller.pages;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.SwingUtilities;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.xy.XYDataset;

import it.example.util.AlertUtil;
import it.example.util.SoundManager;
import it.example.util.chart.ChartUtil;
import it.example.util.chart.DataGenerator;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingNode;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class CodingController {
    // Containers
    @FXML private VBox graficiSection;
    @FXML private SwingNode swingChartsSection;       // Container to display the chart (Swing-based)
    @FXML private HBox soundSection;

    // Control elements
    @FXML private TextField formulaTextField;     // Text field to input formula
    @FXML private TextArea formulaTextArea;        // Text area to show Java code
    @FXML private ChoiceBox<String> choiceBox;     // Choice box to switch between formula and Java code

    // Buttons
    @FXML private Button generateButton;          // Button to generate the chart
    @FXML private Button resetButton;             // Button to reset the layout
    @FXML private Button soundButton;

    private boolean useTextField = true;

    /***************** Methods *****************/

    /** Initializes the controller. */
    @FXML public void initialize() {
        updateSecondarySectionsVisibility(false);
        switchChoiceBox();
        SwingUtilities.invokeLater(() -> { ChartUtil.createChartPanel(swingChartsSection); });
    }

    /** Handles the generate chart button click. */
    @FXML
    private void generateChartButton() {
        String formula = useTextField ? formulaTextField.getText() : handleTextAreaText();

        // Show a loading message or icon to indicate data generation in progress

        // Create a task to generate data in the background
        Task<double[][]> generateDataTask = new Task<>() {
            @Override
            protected double[][] call() {
                // Call the DataGenerator to generate data using the formula
                return DataGenerator.generate(formula);
            }
        };

        // Add an action to be executed once the Task is completed
        generateDataTask.setOnSucceeded(event -> {
            // Get the generated data from the completed Task
            double[][] data = generateDataTask.getValue();
            // Update the ChartUtil dataset with the evaluated data
            ChartUtil.updateChartDataset(data[0], data[1]);

            // Set the formula and update the SoundUtil wave data
            // SoundUtil.setFormula(formula);
            SoundManager.getInstance().setWaveData(data[1]);

            // Update the chart
            updateChart();
        });

        // Start the Task in a separate thread
        Thread generateDataThread = new Thread(generateDataTask);
        generateDataThread.start();

        updateSecondarySectionsVisibility(true);
    }

    /* ********************* SoundUtil Buttons ***********************/

    /** Toggles sound play and stop, and updates button text accordingly. */
    @FXML
    private void handleSound() {
        if ("Play".equals(soundButton.getText())) {
            executeSoundTask(SoundManager.getInstance()::play, "Stop");
        } else {
            executeSoundTask(SoundManager.getInstance()::stop, "Play");
        }
    }

    /**
     * Executes a sound-related task (play or stop) in a background thread and updates the button text upon success.
     * Logs errors to the console if the task fails.
     *
     * @param soundAction The sound action to perform (play or stop).
     * @param buttonText  The text to set on the button after the task succeeds.
     */
    private void executeSoundTask(Runnable soundAction, String buttonText) {
        Task<Void> soundTask = new Task<>() {
            @Override
            protected Void call() {
                try {
                    soundAction.run();
                } catch (Exception e) {
                    System.err.println("Error during sound operation: " + e.getMessage());
                    e.printStackTrace();
                    AlertUtil.showErrorAlert("Error during sound operation", e.getMessage());
                    throw e; // Rethrow to let the task handle it as a failure
                }
                return null;
            }
        };
        soundTask.setOnSucceeded(event -> Platform.runLater(() -> soundButton.setText(buttonText)));
        soundTask.setOnFailed(event -> {
            Throwable exception = soundTask.getException();
            System.err.println("Sound task failed: " + exception.getMessage());
            exception.printStackTrace();
            AlertUtil.showErrorAlert("Sound task failed", exception.getMessage());
        });
        new Thread(soundTask).start();
    }

    /** Handles the reset button click, resetting the UI and stopping any sound. */
    @FXML
    private void resetButton() {
        updateSecondarySectionsVisibility(false);
        resetFieldsAndSections();
        executeSoundTask(SoundManager.getInstance()::stop, "Play");
    }

    // ChoiceBox
    /** Handles the choice box selection change. */
    private void switchChoiceBox() {
        // Method to handle the ChoiceBox selection change
        choiceBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            // Handle choice changes
            switch (newValue) {
                case "Formula":
                    formulaTextField.setVisible(true);
                    formulaTextField.setManaged(true);
                    formulaTextArea.setVisible(false);
                    formulaTextArea.setManaged(false);
                    useTextField = true;
                    break;
                case "Java Code":
                default:
                    formulaTextField.setVisible(false);
                    formulaTextField.setManaged(false);
                    formulaTextArea.setVisible(true);
                    formulaTextArea.setManaged(true);
                    useTextField = false;
                    break;
            }
        });
    }

    /** Handles the text in the text area. */
    private String handleTextAreaText() {
        String formula = formulaTextArea.getText();

        formula = convertIfElseToTernary(formula);

        return formula;
    }

    /** Converts if-else constructs to ternary operators in the input code. */
    private String convertIfElseToTernary(String inputCode) {
        // Regular expression pattern to find "if-else" constructs
        Pattern pattern = Pattern.compile("if \\((.*?)\\) \\{\\s*((?:.|\\s)*?)\\s*\\} else \\{\\s*((?:.|\\s)*?)\\s*\\}");

        // Matcher to find matches
        Matcher matcher = pattern.matcher(inputCode);

        // If the pattern is found, replace it with a ternary operator
        while (matcher.find()) {
            String condition = matcher.group(1).replaceAll(";", "");
            String trueExpression = matcher.group(2);
            String falseExpression = matcher.group(3);

            // Remove trailing semicolons from expressions
            trueExpression = trueExpression.trim().replaceAll(";$", "");
            falseExpression = falseExpression.trim().replaceAll(";$", "");

            String ternaryExpression = "(" + condition + ") ? (" + trueExpression + ") : (" + falseExpression + ")";
            inputCode = inputCode.replace(matcher.group(), ternaryExpression);
        }

        return inputCode;
    }

    // Chart Method
    /** Updates the chart with new data. */
    private void updateChart() {
        // Method to update the chart with new data
        // Get the reference to the updated dataset from ChartUtil
        XYDataset dataset = ChartUtil.getDataset();

        // Get the reference to the chart panel from the SwingNode
        ChartPanel chartPanel = (ChartPanel) swingChartsSection.getContent();

        // Update the chart dataset in the panel
        chartPanel.setChart(ChartFactory.createXYLineChart(
                "Waveform graph",
                "X Frequency",
                "Y Amplitude",
                dataset,
                PlotOrientation.VERTICAL,
                true,
                true,
                false
        ));

        // Request a chart panel update
        chartPanel.repaint();
    }

    /**
     * Updates the visibility and manageability of secondary sections based on the specified state.
     *
     * @param showSections The state to set for visibility and manageability of sections.
     */
    private void updateSecondarySectionsVisibility(boolean showSections) {
        swingChartsSection.setManaged(showSections);
        swingChartsSection.setVisible(showSections);
        soundSection.setManaged(showSections);
        soundSection.setVisible(showSections);

        graficiSection.setManaged(!showSections);
        graficiSection.setVisible(!showSections);
    }

    /**
     * Resets all input fields and secondary sections to their default state.
     */
    private void resetFieldsAndSections() {
        formulaTextArea.setText(null);
        formulaTextField.setText(null);
        updateSecondarySectionsVisibility(false);
    }
}
