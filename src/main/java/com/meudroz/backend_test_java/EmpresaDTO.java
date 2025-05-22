package com.meudroz.backend_test_java;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "EmpresaDTO", description = "Dados da empresa")
public class EmpresaDTO {
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

  @Schema(description = "Telefone da empresa", example = "(11) 12345-6789")
  @Size(max = 11, message = "O telefone pode ter no máximo 20 caracteres.")
  public String telefone;

  public String getNome() {
    return nome;
  }

  public void setNome(String nome) {
    this.nome = nome;
  }

  public String getCnpj() {
    return cnpj;
  }

  public void setCnpj(String cnpj) {
    this.cnpj = cnpj;
  }

  public String getEndereco() {
    return endereco;
  }

  public void setEndereco(String endereco) {
    this.endereco = endereco;
  }

  public String getTelefone() {
    return telefone;
  }

  public void setTelefone(String telefone) {
    this.telefone = telefone;
  }
}