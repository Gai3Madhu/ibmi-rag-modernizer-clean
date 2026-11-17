package org.example;

import java.util.List;
import java.util.regex.Pattern;

public class GuardRailService {
    public static final List<Pattern> BLOCKED_PATTERN = List.of (  Pattern.compile("(?i)password\\s*=\\s*['\"].+['\"]"),
            Pattern.compile("(?i)pwd\\s*=\\s*['\"].+['\"]"),
            // AWS / API keys
            Pattern.compile("(?i)(AKIA[0-9A-Z]{16})"),
            Pattern.compile("(?i)api[_-]?key\\s*=\\s*['\"].+['\"]"),
            // SQL Injection attempt
            Pattern.compile("(?i)(DROP\\s+TABLE|DELETE\\s+FROM|OR\\s+1\\s*=\\s*1)"),
            // SSN / Aadhar - PII should not go to LLM
            Pattern.compile("\\b\\d{4}-\\d{4}-\\d{4}\\b"), // Aadhar pattern
            Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b")  // SSN pattern
    );
    // GATE 1: Check user question BEFORE sending to Gemini
    public void checkInput(String question){
        if(question == null || question.isEmpty()){
            throw new IllegalArgumentException("GUARDTAIL BLOCKED, Empty Question");
        }
        if(question.toLowerCase().contains("password") || question.toLowerCase().contains("show")){
            throw new IllegalArgumentException("GUARDTAIL BLOCKED, Empty Question");
        }
        for (Pattern p : BLOCKED_PATTERN) {
            if (p.matcher(question).find()) {
                throw new IllegalArgumentException(
                        "[GUARDRAIL BLOCKED - INPUT] Question contains sensitive data: " + p.pattern()
                );
            }
        }
        System.out.println("[GUARDRAIL PASSED - INPUT] Question is safe");
    }

    // GATE 2: Check AI answer BEFORE showing to user
    public String checkOutput(String aiAnswer) {
        if (aiAnswer == null) return "No answer from AI";

        for (Pattern p : BLOCKED_PATTERN) {
            if (p.matcher(aiAnswer).find()) {
                System.out.println("[GUARDRAIL BLOCKED - OUTPUT] AI tried to leak secret. Redacting...");
                // Redact instead of blocking fully - better UX
                return aiAnswer.replaceAll("(?i)password\\s*=\\s*['\"].+['\"]", "password = '***REDACTED***'");
            }
        }

        // Check if AI is hallucinating Java conversion
        if (aiAnswer.contains("CustBal") && !aiAnswer.contains("BigDecimal")) {
            System.out.println("[GUARDRAIL WARNING - OUTPUT] CustBal should be BigDecimal, AI missed it. Flagging...");
            return aiAnswer + "\n\n[GUARDRAIL NOTE: CustBal is Packed Decimal 15,2 -> Must be BigDecimal in Java, not double]";
        }

        System.out.println("[GUARDRAIL PASSED - OUTPUT] Answer is safe");
        return aiAnswer;
    }
}
