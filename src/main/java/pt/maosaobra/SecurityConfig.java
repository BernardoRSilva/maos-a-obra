package pt.maosaobra;
import java.io.IOException;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration @EnableMethodSecurity
public class SecurityConfig {
 @Bean PasswordEncoder encoder(){return new BCryptPasswordEncoder(10);}
 @Bean UserDetailsService users(JdbcTemplate db){
  return username -> db.query("SELECT username,password_hash,role FROM volunteers WHERE username=?",
    rs -> {if(!rs.next()) throw new org.springframework.security.core.userdetails.UsernameNotFoundException("Utilizador desconhecido");
    return new User(rs.getString(1),rs.getString(2),java.util.List.of(new SimpleGrantedAuthority("ROLE_"+rs.getString(3))));},username);
 }
 @Bean SecurityFilterChain chain(HttpSecurity http, ObjectMapper json) throws Exception {
  http.authorizeHttpRequests(a -> a.requestMatchers("/","/index.html","/style.css","/app.js","/favicon.ico","/api/csrf","/api/register").permitAll().anyRequest().authenticated())
   .csrf(c -> c.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()).csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
   .formLogin(f -> f.loginProcessingUrl("/api/login").successHandler((req,res,auth)->write(json,res,200,Map.of("message","Sessão iniciada")))
       .failureHandler((req,res,ex)->write(json,res,401,Map.of("error","Credenciais inválidas"))))
   .logout(l -> l.logoutUrl("/api/logout").logoutSuccessHandler((req,res,auth)->write(json,res,200,Map.of("message","Sessão terminada"))))
   .exceptionHandling(e -> e.authenticationEntryPoint((req,res,ex)->write(json,res,401,Map.of("error","É necessário iniciar sessão")))
     .accessDeniedHandler((req,res,ex)->write(json,res,403,Map.of("error","Sem permissão"))));
  return http.build();
 }
 static void write(ObjectMapper mapper,HttpServletResponse res,int status,Map<String,String> body) throws IOException {
  res.setStatus(status);res.setContentType("application/json;charset=UTF-8");mapper.writeValue(res.getWriter(),body);
 }
}
