FROM python:${pythonVersion}-slim

WORKDIR /app

# Install dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application code
COPY app/ ./app/

# Command to run
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]