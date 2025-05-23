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
import org.springframework.web.bind.annotation.RequestParam;
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

@RestController
@RequestMapping("/empresas")
@Tag(name = "Empresas", description = "Endpoints para cadastro e consulta de empresas")
public class EmpresaController {

  private final JdbcTemplate jdbcTemplate;
  private final EmpresaService empresaService;

  private static final Logger logger = LoggerFactory.getLogger(EmpresaController.class);

  public EmpresaController(JdbcTemplate jdbcTemplate, EmpresaService empresaService) {
    this.jdbcTemplate = jdbcTemplate;
    this.empresaService = empresaService;
  }

  @Operation(summary = "Listar todas as empresas")
  @ApiResponse(responseCode = "200", description = "Lista de empresas cadastradas", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(example = """
        {
          "nome": "JAVA TESTE Ltda",
          "cnpj": "12.345.678/0001-12",
          "endereco": "Rua do teste, 123",
          "telefone": "(11) 12345-6789"
        }
      """))))
  @GetMapping(produces = "application/json")
  public ResponseEntity<List<Map<String, Object>>> listarTodasEmpresas() {
    try {
      List<Map<String, Object>> empresas = empresaService.listarEmpresas();
      if (empresas.isEmpty()) {
        return ResponseEntity.noContent().build();
      }
      return ResponseEntity.ok(empresas);
    } catch (Exception e) {
      logger.error("Erro ao listar todas as empresas: {}", e.getMessage(), e);

      Map<String, Object> errorBody = new HashMap<>();
      errorBody.put("erro", "Erro interno ao buscar empresas.");
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(List.of(errorBody));
    }
  }

  @Operation(summary = "Buscar uma empresa pelo CNPJ")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Empresa encontrada ou não encontrada", content = @Content(mediaType = "application/json", schema = @Schema(example = """
            {
              "nome": "JAVA TESTE Ltda",
              "cnpj": "12.345.678/0001-12",
              "endereco": "Rua do teste, 123",
              "telefone": "(11) 12345-6789"
            }
          """)))
  })
  @GetMapping(value = "/buscar", produces = "application/json")
  public ResponseEntity<Object> buscarPorCnpj(@RequestParam String cnpj) {
    String cnpjLimpo;

    try {
      cnpjLimpo = empresaService.limparCnpj(cnpj);

    } catch (NullPointerException e) {
      logger.warn("CNPJ fornecido é nulo ou inválido para limpeza.");
      Map<String, Object> errorBody = new HashMap<>();

      errorBody.put("erro", "CNPJ fornecido é inválido.");

      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody);
    }

    try {
      if (!empresaService.verificaEmpresaCadastrada(cnpjLimpo)) {
        Map<String, Object> responseBodyNotFound = new HashMap<>();
        responseBodyNotFound.put("erro", "Nenhuma empresa encontrada com o CNPJ fornecido.");

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(responseBodyNotFound);
      }

      String sqlQuery = "SELECT nome, cnpj, endereco, telefone FROM empresas WHERE cnpj = ?";
      List<Map<String, Object>> resultadoQuery = jdbcTemplate.queryForList(sqlQuery, cnpjLimpo);

      Map<String, Object> empresaEncontrada = resultadoQuery.get(0);

      Object empresa = empresaEncontrada.get("cnpj");

      String cnpjFormatado = empresaService.formatarCnpj((String) empresa);

      empresaEncontrada.put("cnpj", cnpjFormatado);

      return ResponseEntity.ok(empresaEncontrada);

    } catch (Exception e) {
      logger.error("Erro inesperado ao tentar buscar empresa com CNPJ (limpo) {}: {}", cnpjLimpo, e.getMessage(), e);
      Map<String, Object> responseBodyError = new HashMap<>();

      responseBodyError.put("erro", "Erro inesperado ao tentar buscar a empresa.");

      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(responseBodyError);
    }
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
    String cnpjLimpo = empresaService.limparCnpj(empresa.cnpj);

    try {
      if (empresaService.verificaEmpresaCadastrada(cnpjLimpo)) {
        logger.warn("CNPJ {} já cadastrado", cnpjLimpo);

        responseBody.put("erro", "CNPJ já cadastrado.");

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(responseBody);
      }

      String sql = "INSERT INTO empresas (nome, cnpj, endereco, telefone) VALUES (?, ?, ?, ?)";
      int rows = jdbcTemplate.update(sql, empresa.nome, cnpjLimpo, empresa.endereco, empresa.telefone);

      logger.info("Empresa com CNPJ {} cadastrada com sucesso. Linhas afetadas: {}", cnpjLimpo, rows);

      responseBody.put("mensagem", "Empresa cadastrada com sucesso.");
      responseBody.put("linhasAfetadas", rows);

      return ResponseEntity.status(HttpStatus.CREATED).body(responseBody);

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
  // Permitir alteração de apenas o endereço
  // Adc PATCH?
  @PutMapping(value = "/buscar", consumes = "application/json", produces = "application/json")
  public ResponseEntity<Map<String, Object>> editarEmpresa(@RequestParam String cnpj,
      @RequestBody EmpresaDTO empresa) {
    Map<String, Object> responseBody = new HashMap<>();
    try {

      String sql = "UPDATE empresas SET nome = ?, endereco = ?, telefone = ? WHERE cnpj = ?";
      int rows = jdbcTemplate.update(sql, empresa.nome, empresa.endereco, empresa.telefone, cnpj);

      // // if (!empresaExiste(cnpj)) {
      // // responseBody.put("erro", "Nenhuma empresa encontrada com o CNPJ
      // fornecido.");

      // // return ResponseEntity.status(HttpStatus.NOT_FOUND).body(responseBody);
      // }

      responseBody.put("mensagem", "Empresa atualizada com sucesso.");
      responseBody.put("linhasAfetadas", rows);

      return ResponseEntity.status(HttpStatus.OK).body(responseBody);

    } catch (Exception e) {
      logger.error("Erro inesperado ao tentar atualizar empresa com CNPJ {}: {}", cnpj, e.getMessage(), e);

      Map<String, Object> errorResponseBody = new HashMap<>();

      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponseBody);
    }
  }
}