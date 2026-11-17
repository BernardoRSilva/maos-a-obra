package pt.maosaobra;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DataIntegrityViolationException;
@RestControllerAdvice
class ApiErrors {
 @ExceptionHandler(ResponseStatusException.class)
 ResponseEntity<Map<String,String>> status(ResponseStatusException e){return ResponseEntity.status(e.getStatusCode()).body(Map.of("error",e.getReason()==null?"Pedido inválido":e.getReason()));}
 @ExceptionHandler(MethodArgumentNotValidException.class)
 ResponseEntity<Map<String,String>> validation(){return ResponseEntity.badRequest().body(Map.of("error","Verifique os campos obrigatórios e os respetivos formatos"));}
 @ExceptionHandler(DataIntegrityViolationException.class)
 ResponseEntity<Map<String,String>> data(){return ResponseEntity.status(409).body(Map.of("error","Dados inválidos ou já existentes"));}
}
