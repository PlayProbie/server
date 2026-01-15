# 코드 컨벤션 (Coding Conventions)

> **Source of Truth**: [Naver Hackday Java Conventions](https://naver.github.io/hackday-conventions-java/)

본 프로젝트는 **Naver Hackday Java Conventions**를 따릅니다.
IDE Formatter(`tools/naver-eclipse-formatter.xml`)가 처리하지 못하는 **핵심 규칙**을 아래에 요약합니다.

---

## 1. Naming (이름 짓기)

> **우선순위**: 상세한 네이밍 규칙(Suffix, Class/Method 패턴 등)은 **[Naming Conventions](./naming_conventions.md)** 문서를 따릅니다.
> 아래 내용은 해당 문서에 명시되지 않은 **보조 규칙**입니다.

### 1.1 기본 원칙
- **영어 사용**: 한국어 발음 표기 금지 (예: `moohyungJasan` ❌ -> `intangibleAssets` ✅)
- **1글자 변수 금지**: 임시 변수(`i`, `j` 등)를 제외하고 의미 있는 이름 사용
- **약어(Acronym) 처리**: 약어는 **단어의 첫 글자만 대문자**로 표기
    - `HTTPAPIURL` ❌ -> `HttpApiUrl` ✅
    - `XMLRPC` ❌ -> `XmlRpc` ✅

---

## 2. Declarations (선언)

### 2.1 파일 구조
- **1 파일 1 클래스**: 탑레벨 클래스는 소스 파일당 1개만 존재해야 합니다.
- **파일 인코딩**: `UTF-8`
- **Line Ending**: `LF` (Unix Style)

### 2.2 제한자 순서 (Modifier Order)
Java 표준 순서를 따릅니다.
```java
public protected private abstract static final transient volatile synchronized native strictfp
```

### 2.3 기타
- **Long 리터럴**: 숫자 `1`과 헷갈리는 소문자 `l` 대신 대문자 **`L`** 사용 (예: `30L`)
- **배열 선언**: 대괄호는 타입 뒤에 위치 (예: `String[] args` ✅, `String args[]` ❌)

### 2.4 주석 (Comments)
Formatter가 주석 내부 포맷팅은 수행하지 않으므로 직접 준수해야 합니다.
- **공백 삽입**: 주석 기호(`//`, `/*`) 전후에 공백을 한 칸 둡니다.
    - `//내용` ❌ -> `// 내용` ✅
    - `/*내용*/` ❌ -> `/* 내용 */` ✅


---

## 3. Formatting (자동화)

아래 규칙은 제공된 Formatter(`tools/naver-eclipse-formatter.xml`)를 통해 자동 적용됩니다.
- **Indentation**: Tab (Size: 4)
- **Line Width**: 120자
- **Brace Style**: K&R (End of line)
- **Import Order**: `static`, `java.`, `javax.`, `org.`, `com.` 순서
