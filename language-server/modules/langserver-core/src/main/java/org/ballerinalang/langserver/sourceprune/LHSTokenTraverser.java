/*
  Copyright (c) 2019, WSO2 Inc. (http://wso2.com) All Rights Reserved.
 
  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at
 
  http://www.apache.org/licenses/LICENSE-2.0
 
  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */
package org.ballerinalang.langserver.sourceprune;

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.ballerinalang.langserver.common.utils.CommonUtil;
import org.wso2.ballerinalang.compiler.parser.antlr4.BallerinaParser;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * LHS token traverser.
 * 
 * @since 0.995.0
 */
class LHSTokenTraverser extends AbstractTokenTraverser {
    private List<Integer> lhsTraverseTerminals;
    private List<Integer> definitionKWTerminals;
    private boolean removeDefinition;
    private int rightParenthesisCount;
    private int rightBraceCount;
    private int ltSymbolCount;
    private SourcePruneContext sourcePruneContext;

    LHSTokenTraverser(SourcePruneContext sourcePruneContext) {
        this.sourcePruneContext = sourcePruneContext;
        this.lhsTraverseTerminals = sourcePruneContext.get(SourcePruneKeys.LHS_TRAVERSE_TERMINALS_KEY);
        this.definitionKWTerminals = sourcePruneContext.get(SourcePruneKeys.DEFINITION_KW_TERMINALS_KEY);

        this.removeDefinition = false;
        this.rightParenthesisCount = 0;
        this.rightBraceCount = 0;
        this.ltSymbolCount = 0;
    }

    void traverseLHS(TokenStream tokenStream, int tokenIndex) {
        Optional<Token> token = Optional.of(tokenStream.get(tokenIndex));
        while (token.isPresent()) {
            int type = token.get().getType();
            if (this.lhsTraverseTerminals.contains(type)) {
                boolean terminate = terminateLHSTraverse(token.get(), tokenStream);
                if (terminate) {
                    break;
                }
            }
            if (this.definitionKWTerminals.contains(type)) {
                this.removeDefinition = true;
            }
            alterTokenText(token.get());
            token = CommonUtil.getPreviousDefaultToken(tokenStream, token.get().getTokenIndex());
        }
        
        sourcePruneContext.put(SourcePruneKeys.REMOVE_DEFINITION_KEY, this.removeDefinition);
        sourcePruneContext.put(SourcePruneKeys.RIGHT_PARAN_COUNT_KEY, this.rightParenthesisCount);
        sourcePruneContext.put(SourcePruneKeys.RIGHT_BRACE_COUNT_KEY, this.rightBraceCount);
        sourcePruneContext.put(SourcePruneKeys.LT_COUNT_KEY, this.ltSymbolCount);
    }

    private boolean terminateLHSTraverse(Token token, TokenStream tokenStream) {
        int type = token.getType();
        if (type == BallerinaParser.RIGHT_PARENTHESIS) {
            this.rightParenthesisCount++;
            this.alterTokenText(token);
            return false;
        }
        if (type == BallerinaParser.LEFT_PARENTHESIS) {
            Optional<Token> tokenToLeft = CommonUtil.getPreviousDefaultToken(tokenStream, token.getTokenIndex());
            if (this.rightParenthesisCount > 0) {
                this.rightParenthesisCount--;
                this.alterTokenText(token);
                return false;
            } else if (tokenToLeft.isPresent() && 
                    (BallerinaParser.IF == tokenToLeft.get().getType() ||
                            BallerinaParser.WHILE == tokenToLeft.get().getType())) {
                // Cursor is within the if/ else if/ while condition
                this.replaceCondition(tokenStream, token.getTokenIndex());
            }
            return true;
        }
        /*
        Right brace is used as a terminal token and should be used as a terminating point for block statements.
        in cases such as following example,
            xyz {f1:1, f2:{f3:4}} = 
        right brace cannot consider as a terminal token, therefore we track the previous token and consider that to
        identify blocks. Until the right brace count is zero all the following right braces are altered (refer example)
         */
        if (type == BallerinaParser.RIGHT_BRACE
                && (this.lastAlteredToken == BallerinaParser.ASSIGN || this.rightBraceCount > 0)) {
            this.alterTokenText(token);
            this.rightBraceCount++;
            return false;
        }
        if (type == BallerinaParser.LEFT_BRACE && this.rightBraceCount > 0) {
            this.alterTokenText(token);
            this.rightBraceCount--;
            return false;
        }
        /*
        Specially capture the LT token in order to avoid Right token removal during the following case
        public annotation <resource,r> ...
        RHS Token traverser will check for the less than symbol count when a GT token found
         */
        if (type == BallerinaParser.LT) {
            this.alterTokenText(token);
            this.ltSymbolCount++;
            return false;
        }
        // Handle the ON token replacing since this is used in both service and JSON streaming input
        boolean onServiceRule = CommonUtil.getNDefaultTokensToLeft(tokenStream, 2, token.getTokenIndex()).stream()
                .map(Token::getType)
                .collect(Collectors.toList())
                .contains(BallerinaParser.SERVICE);
        if (token.getType() == BallerinaParser.COMMA || (!onServiceRule && token.getType() == BallerinaParser.ON)) {
            this.alterTokenText(token);
        }
        
        return rightParenthesisCount == 0 && this.rightBraceCount == 0;
    }
}
