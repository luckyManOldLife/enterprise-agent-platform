.PHONY: architecture test java-test python-check contracts-check check

architecture:
	find . -maxdepth 3 -type d | sort

test:
	$(MAKE) java-test
	$(MAKE) python-check
	$(MAKE) contracts-check

java-test:
	mvn -B -ntp -f java-platform/pom.xml test

python-check:
	uv run --project python-services python -c "import sys; assert sys.version_info >= (3, 12)"

contracts-check:
	python3 -c "import json, pathlib; files=list(pathlib.Path('contracts').rglob('*.json')); [json.loads(p.read_text(encoding='utf-8')) for p in files]; print(f'validated {len(files)} JSON contract files')"

check:
	$(MAKE) architecture
	$(MAKE) contracts-check
