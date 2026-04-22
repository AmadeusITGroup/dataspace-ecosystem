# Documentation Setup Guide

This guide walks you through setting up and serving the Dataspace Ecosystem documentation using MkDocs.

## Prerequisites

- Python 3.8 or higher
- Git (to clone the repository)

## 1. Setting Up Python Environment

### Option 1: Using venv (Recommended)

**Create a virtual environment:**
```bash
# Navigate to the project root directory
cd /path/to/dataspace-ecosystem

# Create virtual environment
python3 -m venv venv-docs

# Activate the virtual environment
# On macOS/Linux:
source venv-docs/bin/activate
# On Windows:
# venv-docs\Scripts\activate
```

**Verify activation:**
```bash
which python  # Should show path to venv-docs/bin/python
```

## 2. Installing Dependencies

Once your Python environment is active, install the required packages:

```bash
# Install documentation dependencies
pip install -r requirements-docs.txt

# Verify installation
mkdocs --version
```

**Expected output:**
```
mkdocs, version 1.6.0 (or higher)
```

## 3. Serving Documentation Locally

Start the development server:
```bash
# From the project root directory
mkdocs serve
```

**Expected output:**
```
INFO     -  Building documentation...
INFO     -  Cleaning site directory
INFO     -  Documentation built in X.XX seconds
INFO     -  [XX:XX:XX] Watching paths for changes: 'docs', 'mkdocs.yml'
INFO     -  [XX:XX:XX] Serving on http://127.0.0.1:8000/
```

Open your web browser and navigate to: **http://127.0.0.1:8000/**


## 4. Building Static Documentation

To build the documentation for production deployment:

```bash
# Build static files
mkdocs build

# Build with clean output directory
mkdocs build --clean
```

The built documentation will be available in the `site/` directory.

## 5. Deactivating Environment

When you're done working with the documentation:

**For venv:**
```bash
deactivate
```

## See Also

- [Development Setup](development-setup.md) - IDE and JDK configuration
- [Go Development Setup](go-development-setup.md) - Containerized Go builds
- [Contributing](../contributing.md) - Contribution guidelines

