package com.solesonic.mcp.jira.token;

import com.solesonic.mcp.jira.token.TokenEntity.TokenId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TokenRepository extends JpaRepository<TokenEntity, TokenId> {
}
