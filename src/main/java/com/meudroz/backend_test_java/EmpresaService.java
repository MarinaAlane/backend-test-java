package com.meudroz.backend_test_java;

import com.meudroz.backend_test_java.EmpresaDTO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

  private boolean empresaExiste(String cnpjLimpo) {
    String sql = "SELECT COUNT(*) FROM empresas WHERE cnpj = ?";
    Integer count = jdbcTemplate.queryForObject(sql, Integer.class, cnpjLimpo);
    return count != null && count > 0;
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
   * @param empresaDto DTO com os dados da empresa.
   * @return Um Map contendo os dados da empresa ou vazio se não encontrada.
   * @throws Exception Se o CNPJ não for válido ou ocorrer outro erro.
   */
  public Optional<Map<String, Object>> buscarEmpresas(String cnpjPath) {
    String cnpjLimpo = limparCnpj(cnpjPath);

    if (cnpjLimpo == null || cnpjLimpo.isEmpty()) {

      logger.warn("Tentativa de busca com CNPJ inválido após limpeza: {}", cnpjPath);

      Map<String, Object> erro = new HashMap<>();
      erro.put("erro", "CNPJ inválido após limpeza. Verifique o formato e tente novamente.");
    }

    if (!empresaExiste(cnpjLimpo)) {
      return Optional.empty();
    }

    String sqlQuery = "SELECT nome, cnpj, endereco, telefone FROM empresas WHERE cnpj = ?";
    List<Map<String, Object>> query = jdbcTemplate.queryForList(sqlQuery, cnpjLimpo);

    if (query.isEmpty()) {

      return Optional.empty();
    }

    Map<String, Object> empresa = query.get(0);
    String cnpj = (String) empresa.get("cnpj");
    empresa.put("cnpj", formatarCnpj(cnpj));

    return Optional.of(empresa);
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

    if (cnpjLimpo.length() != 14) {
      throw new IllegalArgumentException("CNPJ inválido. Deve conter 14 dígitos numéricos.");
    }

    if (empresaDto.telefone.length() != 11) {
      throw new IllegalArgumentException("Telefone inválido.");
    }

    if (empresaExiste(cnpjLimpo)) {
      logger.warn("CNPJ {} já cadastrado", cnpjLimpo);

      throw new Exception("CNPJ já cadastrado.");
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
  public Map<String, Object> modificarEmpresa(String cnpjPath, EmpresaDTO empresaDto) throws Exception {
    String cnpjLimpoPath = limparCnpj(cnpjPath);

    if (cnpjLimpoPath == null || cnpjLimpoPath.length() != 14) {
      throw new IllegalArgumentException("CNPJ do path inválido. Deve conter 14 dígitos numéricos.");
    }

    if (!empresaExiste(cnpjLimpoPath)) {

      throw new Exception("Nenhuma empresa encontrada com o CNPJ fornecido para atualização.");
    }

    String sql = "UPDATE empresas SET nome = ?, endereco = ?, telefone = ? WHERE cnpj = ?";
    int rows = jdbcTemplate.update(sql, empresaDto.nome, empresaDto.endereco, empresaDto.telefone, cnpjLimpoPath);
.

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