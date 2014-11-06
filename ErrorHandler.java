import java.io.*;
import java.util.Stack;

public class ErrorHandler {

  private String m_sourceFile;
  private Lexer m_lexer;
  private Token m_prev;
  private Token m_current;
  private Stack<NonT> m_nonTStack;
  private Stack<NonT> m_recoveryStack;
  
  private boolean m_inRecovery;
  private NonT m_recoveryNonT;

  public ErrorHandler(Lexer lexer, String sourceFile) {
    m_lexer = lexer;
    m_sourceFile = sourceFile;
    m_nonTStack = new Stack<NonT>();
    m_inRecovery = false;
  }

  public boolean inRecovery() {
    return m_inRecovery;
  }

  public Token[] tokenMismatch(TokenCode expected, Token actual, Token prevToken) {
    if (m_inRecovery)
      return new Token [] { actual, prevToken};  
    
    reportTokenMismatch(expected, actual, prevToken);
    return recover(actual, prevToken, expected);
  }

  public Token [] noMatch(Token actual, Token prevToken) { 
    if (m_inRecovery)
      return new Token [] { actual, prevToken};
    
    reportNonTNotFound(m_recoveryNonT, actual, prevToken);
    return recover(actual, prevToken, TokenCode.NONE);
  }

  protected Token[] recover(Token actual, Token prevToken, TokenCode expected) {
    m_recoveryStack = new Stack<NonT>();
    m_recoveryNonT = m_nonTStack.peek();
    
    m_inRecovery = true;

    // First, deal with specific known cases
    Token [] tokensRead = specificRecovery(m_recoveryNonT, actual, prevToken, expected);

    if (tokensRead != null)
      return tokensRead;

    // Get the sync tokens for the current nonterminal
    TokenCode [] followTokens = NonT.followTokens(m_nonTStack.peek());

    // Read tokens until we find one of the sync symbols or EOF
    while(true) {
      if (actual.getTokenCode() == TokenCode.EOF)
        break;
      boolean found = false;
      for(int n=0;n<followTokens.length;n++) {
        if (actual.getTokenCode() == followTokens[n]) {
          found = true;
          break;
        }
      }
      if (found)
        break;
      try {
        prevToken = actual;
        actual = m_lexer.yylex();
        trace("Read new token: " + actual.getTokenCode());
      }
      catch (IOException e) {
        System.out.println("IOException reading next token");
        System.exit(1);
      }
    }

    return new Token [] { actual, prevToken};
  }

  protected Token [] specificRecovery(NonT nonT, Token actual, Token prevToken, TokenCode expected) {
    if (nonT == NonT.STATEMENT && prevToken.getTokenCode() == TokenCode.INCDECOP) {
      trace("Firing exception: missing ')' in for");
      m_inRecovery = false;
      return new Token [] { actual, prevToken};
    }
    return null;
  }


  protected void reportTokenMismatch(TokenCode expected, Token actual, Token prevToken) {
    Token errorToken = actual;
    if (expected == TokenCode.SEMICOLON)
      errorToken = prevToken;
    if (!reportStaticMessages(errorToken))
      printErrorInfo("Expected " + TokenCode.getReportableString(expected), errorToken);
    trace("PrevToken: " +  prevToken.getTokenCode() + ", currentToken: " + actual.getTokenCode());
    trace("Inside " + m_nonTStack.peek());
  }

  protected void reportNonTNotFound(NonT expected, Token actual, Token prevToken) {
    Token errorToken = actual;
    if (!reportStaticMessages(errorToken)) {
      
      Stack<NonT> dupl = (Stack<NonT>)m_nonTStack.clone();
      while(true) {
        NonT nonT = dupl.pop();
        String description = NonT.getMainDescr(nonT);
        if (description != null) {
          printErrorInfo("Invalid " + description, errorToken);
          break;
        }
      }
    }
    trace("PrevToken: " +  prevToken.getTokenCode() + ", currentToken: " + actual.getTokenCode());
    trace("Inside " + m_nonTStack.peek());
  }

  protected boolean reportStaticMessages(Token errorToken) {
    if (errorToken.getTokenCode() == TokenCode.ERR_ILL_CHAR) {
      printErrorInfo("Illegal character", errorToken);
      return true;
    }
    else if (errorToken.getTokenCode() == TokenCode.ERR_LONG_ID) {
      printErrorInfo("Identifier too long", errorToken);
      return true;
    }
    return false;
  }

  public void stopNonT() {
    if (m_inRecovery) {
      if (m_recoveryStack.empty()) {
        trace("** Continueing after " + m_nonTStack.peek() + " after recovery");     
        m_inRecovery = false;
        m_nonTStack.pop();
      }
      else {
        trace("** Stopping " + m_recoveryStack.peek() + " (RECOVERY)");
        m_recoveryStack.pop();
      }

    }
    else {
      trace("** Stopping " + m_nonTStack.peek());
      m_nonTStack.pop();
    }
  }

  public void startNonT(NonT nonT) {
    trace("** Starting " + nonT + (m_inRecovery ? " (RECOVERY)" : ""));
    if (m_inRecovery) {
      m_recoveryStack.push(nonT);
    }
    else {
      m_nonTStack.push(nonT);
    }
  }

  

  protected void printErrorInfo(String errorMessage, Token errorToken) {
    String srcLine = getLineFromSource(errorToken.getLineNum());
    String lineNumString = String.valueOf(errorToken.getLineNum());
    while (lineNumString.length() < 3)
      lineNumString = " " + lineNumString;

    System.out.println (lineNumString + ": " + srcLine);
    System.out.print("     ");
    for(int n=0;n<errorToken.getColumnNum();n++)
      System.out.print(" ");
    System.out.print("^ ");
    System.out.println(errorMessage);
    trace(" inside " + m_nonTStack.peek());
  }

  protected String getLineFromSource(int lineNum) {
    FileInputStream fis = null;
    BufferedReader br = null;
    try {
      fis = new FileInputStream(m_sourceFile);
      br = new BufferedReader(new InputStreamReader(fis/*, Charset.forName("UTF-8")*/));
      for(int n=1;n<lineNum;n++) {
        br.readLine();
      }
      return br.readLine();
    }
    catch (IOException e) {
      System.out.println("IOException reading sourcefile");
      System.exit(1);
      return null; 
    }
    finally {
      if (br != null) {
        try { br.close(); } catch (java.io.IOException e) {}
      }
      br = null;
      fis = null;
    }
  }

  protected void trace(String msg) {
    if (MyMain.TRACE)
      System.out.println(msg);
  }
}
