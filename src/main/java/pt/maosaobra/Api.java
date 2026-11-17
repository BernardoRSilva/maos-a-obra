package pt.maosaobra;

import java.security.Principal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
import java.time.*;
import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api")
public class Api {
 private final JdbcTemplate db; private final PasswordEncoder encoder;
 public Api(JdbcTemplate db,PasswordEncoder encoder){this.db=db;this.encoder=encoder;}
 public record Me(long id,String username,String role,String name,LocalDate birthDate,String phone,String skills,String availability){}
 private Me user(Principal p){
  return db.query("SELECT id,username,role,name,birth_date,phone,skills,availability FROM volunteers WHERE username=?",
   rs->{if(!rs.next())throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);return new Me(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getDate(5).toLocalDate(),rs.getString(6),rs.getString(7),rs.getString(8));},p.getName());
 }
 private ResponseStatusException bad(String msg){return new ResponseStatusException(HttpStatus.CONFLICT,msg);}
 @GetMapping("/csrf") public Map<String,String> csrf(HttpServletRequest req){CsrfToken csrf=(CsrfToken)req.getAttribute(CsrfToken.class.getName());return Map.of("token",csrf.getToken(),"header",csrf.getHeaderName());}
 @GetMapping("/me") public Me me(Principal p){return user(p);}
 public record Register(@NotBlank String username,@NotBlank String password,@NotBlank String name,
    @NotNull LocalDate birthDate,@NotBlank String phone,String skills,String availability){}
 @PostMapping("/register") @Transactional public ResponseEntity<Map<String,String>> register(@Valid @RequestBody Register r){
  if(!r.username().matches("[a-zA-Z0-9_]{3,30}")) throw bad("Nome de utilizador: 3–30 letras, números ou _");
  if(r.password().length()<8)throw bad("A password deve ter pelo menos 8 caracteres");
  if(!r.birthDate().isBefore(LocalDate.now()) || r.birthDate().isBefore(LocalDate.now().minusYears(115)))throw bad("Data de nascimento inválida");
  if(db.queryForObject("SELECT count(*) FROM volunteers WHERE username=?",Integer.class,r.username())>0)throw bad("Nome de utilizador já existe");
  db.update("INSERT INTO volunteers(username,password_hash,role,name,birth_date,phone,skills,availability) VALUES(?,?,'VOLUNTEER',?,?,?,?,?)",
     r.username(),encoder.encode(r.password()),r.name().trim(),r.birthDate(),r.phone().trim(),n(r.skills()),n(r.availability()));
  return ResponseEntity.status(201).body(Map.of("message","Conta criada. Inicie sessão."));
 }
 private String n(String s){return s==null?"":s.trim();}
 public record Profile(@NotBlank String name,@NotBlank String phone,String skills,String availability){}
 @PutMapping("/me") public Map<String,String> edit(Principal p,@Valid @RequestBody Profile r){
  db.update("UPDATE volunteers SET name=?,phone=?,skills=?,availability=? WHERE username=?",r.name().trim(),r.phone().trim(),n(r.skills()),n(r.availability()),p.getName());
  return Map.of("message","Perfil atualizado");
 }
 @GetMapping("/shifts") public List<Map<String,Object>> shifts(Principal p){
  long uid=user(p).id();
  return db.query("""
   SELECT s.id,s.title,s.starts_at,s.ends_at,s.place,s.capacity,s.required_skill,s.skill_policy,
    (SELECT count(*) FROM enrollments e WHERE e.shift_id=s.id AND e.status IN ('REGISTERED','PRESENT','ABSENT')) AS occupied,
    (SELECT e.status FROM enrollments e WHERE e.shift_id=s.id AND e.volunteer_id=?) AS my_status
   FROM shifts s ORDER BY s.starts_at,s.id
   """,(rs,i)->{
    Map<String,Object> m=new LinkedHashMap<>();
    m.put("id",rs.getLong("id"));m.put("title",rs.getString("title"));m.put("startsAt",rs.getTimestamp("starts_at").toLocalDateTime().toString());
    m.put("endsAt",rs.getTimestamp("ends_at").toLocalDateTime().toString());m.put("place",rs.getString("place"));
    m.put("capacity",rs.getInt("capacity"));m.put("occupied",rs.getInt("occupied"));
    m.put("requiredSkill",rs.getString("required_skill"));m.put("skillPolicy",rs.getString("skill_policy"));
    m.put("myStatus",rs.getString("my_status"));return m;
   },uid);
 }
 public record ShiftInput(@NotBlank String title,@NotNull LocalDateTime startsAt,@NotNull LocalDateTime endsAt,
     @NotBlank String place,@Min(1) int capacity,String requiredSkill,String skillPolicy){}
 @PreAuthorize("hasRole('COORDINATOR')") @PostMapping("/shifts")
 public ResponseEntity<Map<String,Object>> create(@Valid @RequestBody ShiftInput r){
  if(!r.endsAt().isAfter(r.startsAt()))throw bad("O fim deve ser depois do início");
  if(!r.startsAt().isAfter(LocalDateTime.now()))throw bad("O turno deve começar no futuro");
  if(r.capacity()>500)throw bad("Máximo de 500 vagas");
  String policy=n(r.skillPolicy()).equals("ONE")?"ONE":"ALL";
  Long id=db.queryForObject("INSERT INTO shifts(title,starts_at,ends_at,place,capacity,required_skill,skill_policy) VALUES(?,?,?,?,?,?,?) RETURNING id",
       Long.class,r.title().trim(),r.startsAt(),r.endsAt(),r.place().trim(),r.capacity(),n(r.requiredSkill()),policy);
  return ResponseEntity.status(201).body(Map.of("id",id,"message","Turno criado"));
 }
 @PreAuthorize("hasRole('COORDINATOR')") @PutMapping("/shifts/{id}") @Transactional
 public Map<String,String> update(@PathVariable long id,@Valid @RequestBody ShiftInput r){
  if(!r.endsAt().isAfter(r.startsAt())||!r.startsAt().isAfter(LocalDateTime.now()))throw bad("Datas inválidas");
  db.queryForList("SELECT id FROM shifts WHERE id=? FOR UPDATE",id);
  int occupied=db.queryForObject("SELECT count(*) FROM enrollments WHERE shift_id=? AND status IN ('REGISTERED','PRESENT','ABSENT')",Integer.class,id);
  if(r.capacity()<occupied)throw bad("Não pode reduzir vagas abaixo do número de inscritos");
  if(occupied>0)throw bad("Por segurança, não é possível alterar turnos que já têm inscrições");
  int updated=db.update("UPDATE shifts SET title=?,starts_at=?,ends_at=?,place=?,capacity=?,required_skill=?,skill_policy=? WHERE id=?",
      r.title(),r.startsAt(),r.endsAt(),r.place(),r.capacity(),n(r.requiredSkill()),"ONE".equals(r.skillPolicy())?"ONE":"ALL",id);
  if(updated==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Turno inexistente");
  return Map.of("message","Turno atualizado");
 }
 @PreAuthorize("hasRole('COORDINATOR')") @DeleteMapping("/shifts/{id}") @Transactional
 public Map<String,String> delete(@PathVariable long id){
  int count=db.queryForObject("SELECT count(*) FROM enrollments WHERE shift_id=? AND status IN ('REGISTERED','PRESENT','ABSENT')",Integer.class,id);
  if(count>0)throw bad("Não é permitido apagar um turno com inscrições");
  if(db.update("DELETE FROM shifts WHERE id=?",id)==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND);
  return Map.of("message","Turno eliminado");
 }
 private record Shift(long id,LocalDateTime start,LocalDateTime end,int capacity,String skill,String policy){}
 private Shift lockedShift(long id){
  return db.query("SELECT id,starts_at,ends_at,capacity,required_skill,skill_policy FROM shifts WHERE id=? FOR UPDATE",rs -> {
    if(!rs.next())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Turno inexistente");
    return new Shift(rs.getLong(1),rs.getTimestamp(2).toLocalDateTime(),rs.getTimestamp(3).toLocalDateTime(),rs.getInt(4),rs.getString(5),rs.getString(6));
  },id);
 }
 private boolean skilled(Me u,String required){
  return required.isBlank() || Arrays.stream(u.skills().split(";|,")).anyMatch(s->s.trim().equalsIgnoreCase(required));
 }
 @PreAuthorize("hasRole('VOLUNTEER')") @PostMapping("/shifts/{id}/enroll") @Transactional
 public Map<String,String> enroll(@PathVariable long id,Principal p){
  Me u=user(p); Shift s=lockedShift(id); // bloqueio pessimista impede ultrapassar vagas por concorrência
  db.queryForList("SELECT id FROM volunteers WHERE id=? FOR UPDATE",u.id()); // também serializa inscrições simultâneas do mesmo voluntário
  if(!s.start().isAfter(LocalDateTime.now()))throw bad("Turno já começou");
  if(s.start().toLocalTime().compareTo(LocalTime.of(20,0))>=0 && Period.between(u.birthDate(),s.start().toLocalDate()).getYears()<18)
    throw bad("Menores de 18 anos não podem participar em turnos noturnos (a partir das 20h)");
  int occupied=db.queryForObject("SELECT count(*) FROM enrollments WHERE shift_id=? AND status IN ('REGISTERED','PRESENT','ABSENT')",Integer.class,id);
  if(occupied>=s.capacity())throw bad("Turno sem vagas disponíveis");
  int existing=db.queryForObject("SELECT count(*) FROM enrollments WHERE shift_id=? AND volunteer_id=? AND status IN ('REGISTERED','PRESENT','ABSENT')",Integer.class,id,u.id());
  if(existing>0)throw bad("Já está inscrito neste turno");
  int overlap=db.queryForObject("""
    SELECT count(*) FROM enrollments e JOIN shifts s ON s.id=e.shift_id
    WHERE e.volunteer_id=? AND e.status IN ('REGISTERED','PRESENT','ABSENT')
      AND s.starts_at<? AND s.ends_at>?
    """,Integer.class,u.id(),s.end(),s.start());
  if(overlap>0)throw bad("Já tem uma inscrição num turno com horário sobreposto");
  if(!skilled(u,s.skill())){
    if(s.policy().equals("ALL"))throw bad("É necessária a competência: "+s.skill());
    int qualified=db.queryForObject("""
      SELECT count(*) FROM enrollments e JOIN volunteers v ON v.id=e.volunteer_id
      WHERE e.shift_id=? AND e.status IN ('REGISTERED','PRESENT','ABSENT')
      AND lower(v.skills) LIKE lower(?)
    """,Integer.class,id,"%"+s.skill()+"%");
    if(qualified==0)throw bad("É necessário que se inscreva primeiro uma pessoa com: "+s.skill());
  }
  db.update("""
   INSERT INTO enrollments(shift_id,volunteer_id,status) VALUES(?,?,'REGISTERED')
   ON CONFLICT(shift_id,volunteer_id) DO UPDATE SET status='REGISTERED'
  """,id,u.id());
  return Map.of("message","Inscrição confirmada");
 }
 @PreAuthorize("hasRole('VOLUNTEER')") @DeleteMapping("/shifts/{id}/enroll") @Transactional
 public Map<String,String> cancel(@PathVariable long id,Principal p){
  Me u=user(p);Shift s=lockedShift(id);
  if(!s.start().isAfter(LocalDateTime.now().plusHours(24)))throw bad("Cancelamentos só até 24 horas antes. Contacte a coordenação.");
  int status=db.queryForObject("SELECT count(*) FROM enrollments WHERE shift_id=? AND volunteer_id=? AND status='REGISTERED'",Integer.class,id,u.id());
  if(status==0)throw bad("Não existe inscrição ativa cancelável");
  if(s.policy().equals("ONE") && !s.skill().isBlank() && skilled(u,s.skill())){
    int qualified=db.queryForObject("""
     SELECT count(*) FROM enrollments e JOIN volunteers v ON v.id=e.volunteer_id
     WHERE e.shift_id=? AND e.status='REGISTERED' AND lower(v.skills) LIKE lower(?)
    """,Integer.class,id,"%"+s.skill()+"%");
    int others=db.queryForObject("SELECT count(*) FROM enrollments WHERE shift_id=? AND volunteer_id<>? AND status='REGISTERED'",Integer.class,id,u.id());
    if(qualified<=1 && others>0)throw bad("Contacte a coordenação: é a única pessoa com a competência obrigatória");
  }
  db.update("UPDATE enrollments SET status='CANCELLED' WHERE shift_id=? AND volunteer_id=?",id,u.id());
  return Map.of("message","Inscrição cancelada");
 }
 @PreAuthorize("hasRole('COORDINATOR')") @GetMapping("/volunteers")
 public List<Map<String,Object>> volunteers(){
  return db.query("SELECT id,username,name,phone,birth_date,skills,availability FROM volunteers ORDER BY name",(rs,i)->Map.of(
     "id",rs.getLong("id"),"username",rs.getString("username"),"name",rs.getString("name"),"phone",rs.getString("phone"),
     "birthDate",rs.getDate("birth_date").toString(),"skills",rs.getString("skills"),"availability",rs.getString("availability")));
 }
 @PreAuthorize("hasRole('COORDINATOR')") @GetMapping("/shifts/{id}/participants")
 public List<Map<String,Object>> participants(@PathVariable long id){
  return db.query("""
   SELECT e.volunteer_id,v.name,v.skills,e.status FROM enrollments e
    JOIN volunteers v ON v.id=e.volunteer_id
    WHERE e.shift_id=? AND e.status<>'CANCELLED' ORDER BY v.name
  """,(rs,i)->Map.of("volunteerId",rs.getLong(1),"name",rs.getString(2),"skills",rs.getString(3),"status",rs.getString(4)),id);
 }
 public record Presence(@NotNull Long volunteerId,@NotBlank String status){}
 @PreAuthorize("hasRole('COORDINATOR')") @PutMapping("/shifts/{id}/presence") @Transactional
 public Map<String,String> presence(@PathVariable long id,@Valid @RequestBody Presence r){
  if(!List.of("PRESENT","ABSENT","REGISTERED").contains(r.status()))throw bad("Estado inválido");
  int n=db.update("UPDATE enrollments SET status=? WHERE shift_id=? AND volunteer_id=? AND status<>'CANCELLED'",r.status(),id,r.volunteerId());
  if(n==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Inscrição inexistente");
  return Map.of("message","Presença atualizada");
 }
 @GetMapping("/hours") public Map<String,Object> hours(Principal p){
  Me u=user(p); return hoursFor(u.id());
 }
 @PreAuthorize("hasRole('COORDINATOR')") @GetMapping("/volunteers/{id}/hours")
 public Map<String,Object> hoursAdmin(@PathVariable long id){return hoursFor(id);}
 private Map<String,Object> hoursFor(long uid){
  Double historic=db.queryForObject("SELECT COALESCE(SUM(hours),0) FROM historical_attendance WHERE volunteer_id=? AND present=true",Double.class,uid);
  Double current=db.queryForObject("""
   SELECT COALESCE(SUM(EXTRACT(EPOCH FROM (s.ends_at-s.starts_at))/3600.0),0)
   FROM enrollments e JOIN shifts s ON s.id=e.shift_id WHERE e.volunteer_id=? AND e.status='PRESENT'
  """,Double.class,uid);
  return Map.of("hours",Math.round((historic+current)*100.0)/100.0,"historical",historic,"newHours",current);
 }
 @PreAuthorize("hasRole('COORDINATOR')") @GetMapping("/dashboard")
 public List<Map<String,Object>> dashboard(){
  return db.query("""
   SELECT s.id,s.title,s.starts_at,s.capacity,
   s.capacity-(SELECT count(*) FROM enrollments e WHERE e.shift_id=s.id AND e.status IN ('REGISTERED','PRESENT','ABSENT')) AS free
   FROM shifts s WHERE s.starts_at>=CURRENT_TIMESTAMP AND s.starts_at<CURRENT_TIMESTAMP+INTERVAL '7 days'
   ORDER BY s.starts_at
  """,(rs,i)->Map.of("id",rs.getLong(1),"title",rs.getString(2),"startsAt",rs.getTimestamp(3).toLocalDateTime().toString(),"capacity",rs.getInt(4),"free",rs.getInt(5)));
 }
}
