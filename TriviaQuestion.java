import java.util.List;

public class TriviaQuestion {
    private String question;
    private List<String> options;
    private String correctAnswer;

    public TriviaQuestion(String question, List<String> options, String correctAnswer) {
        this.question = question;
        this.options = options;
        this.correctAnswer = correctAnswer;
    }

    public String getQuestion() {
        return question;
    }

    public List<String> getOptions() {
        return options;
    }

    public String getCorrectAnswer() {
        return correctAnswer;
    }

    public String toString() {
        return question + options.toString();
    }
}
