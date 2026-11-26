# Ubuntu VM that runs the Jenkins build server (ci/jenkins) with Docker, so image builds do not need a laptop.
#
# cloud-init installs Docker, adds swap for the Maven/npm builds and clones the repository. Jenkins itself is
# started after `apply` by ci/scripts/configure-jenkins-vm.sh, which copies ci/jenkins/.env over SSH, so the
# GitHub token and passwords never go into VM metadata or Terraform state.

resource "tls_private_key" "jenkins" {
  algorithm = "RSA"
  rsa_bits  = 4096
}

resource "azurerm_virtual_network" "jenkins" {
  name                = "vnet-${var.name_prefix}-jenkins"
  resource_group_name = data.azurerm_resource_group.this.name
  location            = local.location
  address_space       = ["10.20.0.0/24"]
  tags                = var.tags
}

resource "azurerm_subnet" "jenkins" {
  name                 = "snet-jenkins"
  resource_group_name  = data.azurerm_resource_group.this.name
  virtual_network_name = azurerm_virtual_network.jenkins.name
  address_prefixes     = ["10.20.0.0/26"]
}

resource "azurerm_network_security_group" "jenkins" {
  name                = "nsg-${var.name_prefix}-jenkins"
  resource_group_name = data.azurerm_resource_group.this.name
  location            = local.location
  tags                = var.tags

  security_rule {
    name                       = "allow-ssh"
    priority                   = 1000
    direction                  = "Inbound"
    access                     = "Allow"
    protocol                   = "Tcp"
    source_port_range          = "*"
    destination_port_range     = "22"
    source_address_prefix      = var.ssh_allowed_cidr
    destination_address_prefix = "*"
  }

  security_rule {
    name                       = "allow-jenkins-ui"
    priority                   = 1010
    direction                  = "Inbound"
    access                     = "Allow"
    protocol                   = "Tcp"
    source_port_range          = "*"
    destination_port_range     = "8081"
    source_address_prefix      = var.jenkins_allowed_cidr
    destination_address_prefix = "*"
  }
}

resource "azurerm_subnet_network_security_group_association" "jenkins" {
  subnet_id                 = azurerm_subnet.jenkins.id
  network_security_group_id = azurerm_network_security_group.jenkins.id
}

resource "azurerm_public_ip" "jenkins" {
  name                = "pip-${var.name_prefix}-jenkins"
  resource_group_name = data.azurerm_resource_group.this.name
  location            = local.location
  allocation_method   = "Static"
  sku                 = "Standard"
  tags                = var.tags
}

resource "azurerm_network_interface" "jenkins" {
  name                = "nic-${var.name_prefix}-jenkins"
  resource_group_name = data.azurerm_resource_group.this.name
  location            = local.location
  tags                = var.tags

  ip_configuration {
    name                          = "primary"
    subnet_id                     = azurerm_subnet.jenkins.id
    private_ip_address_allocation = "Dynamic"
    public_ip_address_id          = azurerm_public_ip.jenkins.id
  }
}

resource "azurerm_linux_virtual_machine" "jenkins" {
  name                            = "vm-${var.name_prefix}-jenkins"
  resource_group_name             = data.azurerm_resource_group.this.name
  location                        = local.location
  size                            = var.jenkins_vm_size
  admin_username                  = "azureuser"
  disable_password_authentication = true
  network_interface_ids           = [azurerm_network_interface.jenkins.id]
  tags                            = var.tags

  custom_data = base64encode(templatefile("${path.module}/cloud-init.yaml.tftpl", {
    admin_user  = "azureuser"
    repo_url    = var.repo_url
    repo_branch = var.repo_branch
    repo_dir    = "/opt/invigilatorassist"
  }))

  admin_ssh_key {
    username   = "azureuser"
    public_key = tls_private_key.jenkins.public_key_openssh
  }

  os_disk {
    caching              = "ReadWrite"
    storage_account_type = "Standard_LRS"
    disk_size_gb         = 64
  }

  source_image_reference {
    publisher = "Canonical"
    offer     = "ubuntu-24_04-lts"
    sku       = "server"
    version   = "latest"
  }

  # A changed cloud-init script should not rebuild a running Jenkins.
  lifecycle {
    ignore_changes = [custom_data]
  }

  depends_on = [azurerm_subnet_network_security_group_association.jenkins]
}
