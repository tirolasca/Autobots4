package com.autobots.automanager.configuracao;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.autobots.automanager.adaptadores.UserDetailsServiceImpl;
import com.autobots.automanager.filtros.Autorizador;
import com.autobots.automanager.jwt.ProvedorJwt;

/**
 * Segurança da API: sem sessão (stateless), autenticação por token JWT e
 * autorização por perfil via @PreAuthorize nos controles. A única rota pública
 * é POST /login.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class Seguranca {

	/** Senhas são guardadas apenas como hash BCrypt. */
	@Bean
	public PasswordEncoder codificadorSenha() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public SecurityFilterChain cadeiaDeFiltros(HttpSecurity http, ProvedorJwt provedorJwt,
			UserDetailsServiceImpl servico) throws Exception {
		http.csrf(csrf -> csrf.disable())
				.sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				// sem token válido: 401 (e não 403) nas rotas protegidas
				.exceptionHandling(
						excecoes -> excecoes.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
				.authorizeHttpRequests(rotas -> rotas
						.requestMatchers(HttpMethod.POST, "/login").permitAll()
						// o Spring Boot encaminha as respostas de erro (400, 404, 409...) para /error
						.requestMatchers("/error").permitAll()
						.anyRequest().authenticated())
				.addFilterBefore(new Autorizador(provedorJwt, servico), UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}
}
