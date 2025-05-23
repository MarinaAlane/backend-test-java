package com.meudroz.backend_test_java;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class EmpresaService {

  private static final Logger logger = LoggerFactory.getLogger(EmpresaService.class);
  private final JdbcTemplate jdbcTemplate;

  @Autowired
  public EmpresaService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  private boolean verificaEmpresaCadastrada(String cnpj) {
    String sql = "SELECT COUNT(*) FROM empresas WHERE cnpj = ?";
    Integer count = jdbcTemplate.queryForObject(sql, Integer.class, cnpj);
    return count != null && count > 0;
  }

  private String verificaCnpj(String cnpj) {
    if (cnpj.length() != 14) {
      logger.warn("CNPJ fornecido é nulo ou inválido para limpeza.");

      Map<String, Object> errorBody = new HashMap<>();
      errorBody.put("erro", "CNPJ fornecido é inválido.");
      return "CNPJ inválido";
    }

    return cnpj;
  }

  private String formatarCnpj(String cnpj) {
    return cnpj.replaceAll("(\\d{2})(\\d{3})(\\d{3})(\\d{4})(\\d{2})", "$1.$2.$3/$4-$5");
  }

  private String limparCnpj(String cnpj) {
    return cnpj.replaceAll("[^0-9]", "");
  }

  /**
   * Lista todas as empresas cadastradas.
   * 
   * @param empresaDto DTO com os dados da empresa.
   * @return Um Map contendo os dados da empresa ou vazio se não encontrada.
   * @throws Exception Se o CNPJ não for válido ou ocorrer outro erro.
   */
  public List<Map<String, Object>> listarEmpresas() {
    String sql = "SELECT nome, cnpj, endereco, telefone FROM empresas";
    List<Map<String, Object>> empresas = jdbcTemplate.queryForList(sql);

    for (Map<String, Object> empresa : empresas) {
      String cnpj = (String) empresa.get("cnpj");
      empresa.put("cnpj", formatarCnpj(cnpj));
    }
    return empresas;
  }

  /**
   * Busca empresa por CNPJ.
   * 
   * @param cnpj O CNPJ da empresa a ser buscada.
   * @return Um ResponseEntity contendo os dados da empresa ou mensagem de erro.
   */
  public Optional<Map<String, Object>> buscarPorCnpj(String cnpjPath) {
    String cnpjLimpo = limparCnpj(cnpjPath);

    if (cnpjLimpo.length() != 14) {
      logger.warn("Tentativa de busca com CNPJ inválido (após limpeza não tem 14 dígitos): {}", cnpjPath);

      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("error", "CNPJ inválido. Deve conter 14 dígitos numéricos.");
      return Optional.of(errorResponse);
    }

    String sqlQuery = "SELECT nome, cnpj, endereco, telefone FROM empresas WHERE cnpj = ?";
    try {
      Map<String, Object> empresa = jdbcTemplate.queryForMap(sqlQuery, cnpjLimpo);
      String cnpj = (String) empresa.get("cnpj");

      empresa.put("cnpj", formatarCnpj(cnpj));

      logger.info("Empresa encontrada para o CNPJ (limpo): {}", cnpjLimpo);

      return Optional.of(empresa);
    } catch (EmptyResultDataAccessException e) {
      logger.info("Nenhuma empresa encontrada para o CNPJ (limpo): {}", cnpjLimpo);

      Map<String, Object> empresa = new HashMap<>();
      empresa.put("erro", "Nenhuma empresa encontrada com o CNPJ fornecido.");

      return Optional.of(empresa);
    }
  }

  /**
   * Cadastra uma nova empresa.
   * 
   * @param empresaDto DTO com os dados da empresa.
   * @return Um Map contendo a mensagem de sucesso e as linhas afetadas.
   * @throws Exception Se o CNPJ já existir ou ocorrer outro erro.
   */
  public Map<String, Object> criarEmpresa(EmpresaDTO empresaDto) throws Exception {
    String cnpjLimpo = limparCnpj(empresaDto.cnpj);

    verificaCnpj(cnpjLimpo);

    if (empresaDto.telefone.length() != 11) {
      logger.warn("Telefone inválido: {}", empresaDto.telefone);

      Map<String, Object> errorBody = new HashMap<>();
      errorBody.put("erro", "Telefone inválido.");
    }

    if (verificaEmpresaCadastrada(cnpjLimpo)) {
      logger.warn("CNPJ {} já cadastrado", cnpjLimpo);

      Map<String, Object> errorBody = new HashMap<>();
      errorBody.put("erro", "CNPJ já cadastrado.");
    }

    String sql = "INSERT INTO empresas (nome, cnpj, endereco, telefone) VALUES (?, ?, ?, ?)";
    int rows = jdbcTemplate.update(sql, empresaDto.nome, cnpjLimpo, empresaDto.endereco, empresaDto.telefone);

    logger.info("Empresa com CNPJ {} cadastrada com sucesso. Linhas afetadas: {}", cnpjLimpo, rows);

    Map<String, Object> resultado = new HashMap<>();
    resultado.put("mensagem", "Empresa cadastrada com sucesso.");
    resultado.put("linhasAfetadas", rows);

    resultado.put("empresa", Map.of(
        "nome", empresaDto.nome,
        "cnpj", formatarCnpj(cnpjLimpo),
        "endereco", empresaDto.endereco,
        "telefone", empresaDto.telefone));
    return resultado;
  }

  /**
   * Atualiza os dados de uma empresa existente.
   * 
   * @param cnpjPath   O CNPJ da empresa a ser atualizada (usado como chave).
   * @param empresaDto DTO com os novos dados da empresa.
   * @return Um Map contendo a mensagem de sucesso e as linhas afetadas.
   * @throws Exception Se a empresa não for encontrada ou ocorrer outro erro.
   */
  public Map<String, Object> EditarEmpresa(String cnpjPath, EmpresaDTO empresaDto) throws Exception {
    String cnpjLimpoPath = limparCnpj(cnpjPath);

    verificaCnpj(cnpjLimpoPath);

    if (!verificaEmpresaCadastrada(cnpjLimpoPath)) {

      throw new Exception("Nenhuma empresa encontrada com o CNPJ fornecido para atualização.");
    }

    String sql = "UPDATE empresas SET nome = ?, endereco = ?, telefone = ? WHERE cnpj = ?";
    int rows = jdbcTemplate.update(sql, empresaDto.nome, empresaDto.endereco, empresaDto.telefone, cnpjLimpoPath);

    if (rows == 0) {
      logger.warn("Nenhuma linha foi atualizada para o CNPJ {}, embora a empresa exista. Os dados podem ser os mesmos.",
          cnpjLimpoPath);
    }

    logger.info("Empresa com CNPJ {} tentada atualização. Linhas afetadas: {}", cnpjLimpoPath, rows);

    Map<String, Object> resultado = new HashMap<>();
    resultado.put("mensagem", "Empresa atualizada com sucesso.");
    resultado.put("linhasAfetadas", rows);

    resultado.put("empresa", Map.of(
        "nome", empresaDto.nome,
        "cnpj", formatarCnpj(cnpjLimpoPath),
        "endereco", empresaDto.endereco,
        "telefone", empresaDto.telefone));

    return resultado;
  }
}