package com.meudroz.backend_test_java;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/empresas")
@Tag(name = "Empresas", description = "Endpoints para cadastro e consulta de empresas")
public class EmpresaController {

  private final JdbcTemplate jdbcTemplate;

  private static final Logger logger = LoggerFactory.getLogger(EmpresaController.class);

  public EmpresaController(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  private boolean empresaExiste(String cnpj) {
    String sql = "SELECT COUNT(*) FROM empresas WHERE cnpj = ?";

    Integer count = jdbcTemplate.queryForObject(sql, Integer.class, cnpj);
    return count != null && count > 0;
  }

  @Schema(name = "EmpresaDTO", description = "Dados da empresa")
  public static class EmpresaDTO {
    @Schema(description = "Nome da empresa", example = "JAVA TESTE Ltda")
    @Size(max = 100, message = "O nome pode ter no máximo 100 caracteres.")
    @NotBlank(message = "O nome é obrigatório.")
    public String nome;

    @Schema(description = "CNPJ da empresa", example = "12345678000112")
    @Size(min = 14, max = 14, message = "O CNPJ deve ter exatamente 14 dígitos.")
    @NotBlank(message = "O CNPJ é obrigatório.")
    public String cnpj;

    @Schema(description = "Endereço da empresa", example = "Rua do teste, 123")
    @Size(max = 200, message = "O endereço pode ter no máximo 200 caracteres.")
    public String endereco;
  }

  @Operation(summary = "Listar todas as empresas")
  @ApiResponse(responseCode = "200", description = "Lista de empresas cadastradas", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(example = """
        {
          "nome": "JAVA TESTE Ltda",
          "cnpj": "12.345.678/0001-12",
          "endereco": "Rua do teste, 123"
        }
      """))))
  @GetMapping(produces = "application/json")
  public List<Map<String, Object>> listarEmpresas() {
    String sql = "SELECT nome, cnpj, endereco FROM empresas";
    List<Map<String, Object>> empresas = jdbcTemplate.queryForList(sql);

    for (Map<String, Object> empresa : empresas) {
      String cnpj = (String) empresa.get("cnpj");
      empresa.put("cnpj", cnpj.replaceAll("(\\d{2})(\\d{3})(\\d{3})(\\d{4})(\\d{2})", "$1.$2.$3/$4-$5"));
    }

    return empresas;
  }

  @Operation(summary = "Buscar uma empresa pelo CNPJ")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Empresa encontrada ou não encontrada", content = @Content(mediaType = "application/json", schema = @Schema(example = """
            {
              "nome": "JAVA TESTE Ltda",
              "cnpj": "12.345.678/0001-12",
              "endereco": "Rua do teste, 123"
            }
          """)))
  })
  @GetMapping(value = "/{cnpj}", produces = "application/json")
  public Object buscarPorCnpj(@PathVariable String cnpj) {
    String sql = "SELECT nome, cnpj, endereco FROM empresas WHERE cnpj = ?";
    List<Map<String, Object>> resultado = jdbcTemplate.queryForList(sql, cnpj);

    if (resultado.isEmpty()) {
      return Map.of("erro", "Empresa não encontrada com o CNPJ fornecido.");
    }

    Map<String, Object> empresa = resultado.get(0);
    String cnpjFormatado = ((String) empresa.get("cnpj")).replaceAll("(\\d{2})(\\d{3})(\\d{3})(\\d{4})(\\d{2})",
        "$1.$2.$3/$4-$5");
    empresa.put("cnpj", cnpjFormatado);

    return empresa;
  }

  @Operation(summary = "Cadastrar uma nova empresa")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Empresa cadastrada ou erro de validação", content = @Content(mediaType = "application/json", schema = @Schema(example = """
            {
              "mensagem": "Empresa cadastrada com sucesso.",
              "linhasAfetadas": 1
            }
          """)))
  })
  @PostMapping(consumes = "application/json", produces = "application/json")
  public ResponseEntity<Map<String, Object>> cadastrarEmpresa(@Valid @RequestBody EmpresaDTO empresa) {
    Map<String, Object> responseBody = new HashMap<>();
    String cnpjLimpo = empresa.cnpj.replaceAll("[^0-9]", "");

    try {
      if (empresaExiste(cnpjLimpo)) {
        logger.warn("CNPJ {} já cadastrado", cnpjLimpo);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(responseBody);
      }

      String sql = "INSERT INTO empresas (nome, cnpj, endereco) VALUES (?, ?, ?)";
      int rows = jdbcTemplate.update(sql, empresa.nome, cnpjLimpo, empresa.endereco);

      logger.info("Empresa com CNPJ {} cadastrada com sucesso. Linhas afetadas: {}", cnpjLimpo, rows);

      return ResponseEntity.status(HttpStatus.OK).body(responseBody);

    } catch (Exception e) {
      logger.error("Erro inesperado ao tentar cadastrar CNPJ {}: {}", cnpjLimpo, e.getMessage(), e);

      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(responseBody);
    }
  }

  @Operation(summary = "Atualizar dados de uma empresa pelo CNPJ")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Empresa atualizada ou erro de validação", content = @Content(mediaType = "application/json", schema = @Schema(example = """
            {
              "mensagem": "Empresa atualizada com sucesso.",
              "linhasAfetadas": 1
            }
          """)))
  })
  @PutMapping(value = "/{cnpj}", consumes = "application/json", produces = "application/json")
  public Map<String, Object> atualizarEmpresa(@PathVariable String cnpj, @RequestBody EmpresaDTO empresa) {
    Map<String, Object> response = new HashMap<>();

    if (empresa.nome == null || empresa.nome.trim().isEmpty()) {
      response.put("erro", "O nome é obrigatório.");
      return response;
    }
    if (empresa.nome.length() > 100) {
      response.put("erro", "O nome pode ter no máximo 100 caracteres.");
      return response;
    }

    if (empresa.endereco != null && empresa.endereco.length() > 200) {
      response.put("erro", "O endereço pode ter no máximo 200 caracteres.");
      return response;
    }

    String sql = "UPDATE empresas SET nome = ?, endereco = ? WHERE cnpj = ?";
    int rows = jdbcTemplate.update(sql, empresa.nome, empresa.endereco, cnpj);

    if (rows == 0) {
      response.put("erro", "Nenhuma empresa encontrada com o CNPJ fornecido.");
      return response;
    }

    response.put("mensagem", "Empresa atualizada com sucesso.");
    response.put("linhasAfetadas", rows);
    return response;
  }
}
